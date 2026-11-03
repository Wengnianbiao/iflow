import '../src/monaco'
import * as monaco from 'monaco-editor'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { flushSync } from 'react-dom'
import { interpret } from 'xstate'
import { WorkflowDialogs } from '../src/pages/definition/dialogs/WorkflowDialogs'
import { workflowDefinitionMachine } from '../src/pages/definition/state/machine'

const result = document.querySelector<HTMLPreElement>('#result')!
const editors = document.querySelector<HTMLDivElement>('#editors')!
const run = document.querySelector<HTMLButtonElement>('#run')!

function container() {
  const element = document.createElement('div')
  element.style.cssText = 'height: 240px; border: 1px solid #ccc; margin: 8px 0'
  editors.append(element)
  return element
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message)
}

async function waitFor(condition: () => boolean) {
  const deadline = Date.now() + 5000
  while (!condition()) {
    assert(Date.now() < deadline, '等待编辑器状态超时')
    await new Promise(resolve => setTimeout(resolve, 20))
  }
}

async function checkSaveDialog() {
  const workflow = { route: 'lifecycle-test', name: '生命周期测试', description: '原始定义', version: 1, tasks: [] }
  const actor = interpret(workflowDefinitionMachine.withConfig({
    services: { loadWorkflowDefinition: async () => ({ workflow, versions: [1] }) },
  })).start()
  actor.send({ type: 'INITIALIZE', workflowRoute: workflow.route, workflowVersion: 1, isNewWorkflow: false })
  await waitFor(() => actor.state.matches('ready'))
  actor.send({ type: 'WORKFLOW_CHANGED', workflow: { ...actor.state.context.workflow!, description: '修改后的定义' } })
  const root = createRoot(container())
  flushSync(() => root.render(<StrictMode><WorkflowDialogs actor={actor} /></StrictMode>))
  let mounted = true
  try {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      flushSync(() => actor.send({ type: 'REQUEST_SAVE' }))
      await waitFor(() => monaco.editor.getDiffEditors().some(editor => editor.getModel() !== null))
      const models = monaco.editor.getDiffEditors()[0].getModel()!
      if (attempt < 2) {
        flushSync(() => actor.send({ type: 'CANCEL_DIALOG' }))
      } else {
        flushSync(() => root.unmount())
        mounted = false
      }
      await waitFor(() => monaco.editor.getDiffEditors().length === 0)
      assert(models.original.isDisposed() && models.modified.isDisposed(), '关闭或卸载弹窗后必须释放两侧模型')
    }
  } finally {
    if (mounted) root.unmount()
    actor.stop()
  }
}

run.onclick = async () => {
  run.disabled = true
  editors.replaceChildren()
  result.textContent = '运行中…'
  const errors: string[] = []
  const onError = (event: ErrorEvent) => errors.push(event.error?.stack ?? event.message)
  window.addEventListener('error', onError)
  const editor = monaco.editor.create(container(), { value: 'const value = 1;\nval', language: 'javascript', useShadowDOM: false })
  const model = editor.getModel()!
  try {
    const diff = monaco.editor.createDiffEditor(container(), { readOnly: true })
    diff.dispose()

    // Keep the first editor alive while the last-created diff editor is disposed.
    // Its first completion must construct SuggestWidget through a live hover factory.
    editor.focus()
    editor.setPosition({ lineNumber: 2, column: 4 })
    await editor.getAction('editor.action.triggerSuggest')!.run()
    await new Promise(resolve => setTimeout(resolve, 500))
    assert(editors.querySelector('.suggest-widget'), '必须能创建补全窗口')
    editor.trigger('test', 'hideSuggestWidget', {})
    await editor.getAction('actions.find')!.run()
    assert(editors.querySelector('.find-widget'), '必须能创建查找窗口')
    editor.trigger('test', 'closeFindWidget', {})
    await editor.getAction('editor.action.showContextMenu')!.run()
    assert(document.querySelector('.context-view .monaco-menu'), '必须能创建右键菜单')
    await checkSaveDialog()
    await new Promise(resolve => setTimeout(resolve, 100))
    if (errors.length) throw new Error(errors.join('\n'))
    result.textContent = 'PASS：关闭差异编辑器后补全、查找、右键菜单正常；保存弹窗重复开关和卸载均释放模型。'
  } catch (error) {
    result.textContent = `FAIL：${error instanceof Error ? error.stack : error}`
  } finally {
    window.removeEventListener('error', onError)
    run.disabled = false
    editor.dispose()
    model.dispose()
  }
}
