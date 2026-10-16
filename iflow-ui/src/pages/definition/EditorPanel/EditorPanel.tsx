import { useSelector } from '@xstate/react'
import { useEffect, useRef } from 'react'
import { CaretDoubleRightIcon } from '@phosphor-icons/react'
import { getTaskPlugin } from '@/tasks'
import { buildJoinTaskOptions, buildVariableOptions, findTask } from '@/flow/workflowEditor'
import type { ActorRefFrom } from 'xstate'
import { CodeInspector } from './CodeInspector'
import { TaskInspector } from './TaskInspector'
import { WorkflowInspector } from './WorkflowInspector'
import {
  workflowEditorMachine,
  type WorkflowDefinitionActor,
} from '../state'
import { ValidationScope } from '../validation/ValidationScope'
import { ErrorInspector } from '../validation/ErrorInspector'
import { draftProblems } from '../state/definitionValidation'

function DerivedEditorContent({
  actor,
  definitionActor,
  onCollapse,
}: {
  actor: ActorRefFrom<typeof workflowEditorMachine>
  definitionActor: WorkflowDefinitionActor
  onCollapse: () => void
}) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const definitionState = useSelector(definitionActor, (snapshot) => snapshot)
  const context = definitionState.context
  const contentRef = useRef<HTMLDivElement>(null)
  const { workflow, selectedTask, selectedTaskEditorKey, variableOptions } = state.context
  const inputVariableOptions = workflow && selectedTask ? buildVariableOptions(workflow, selectedTask, false) : variableOptions
  const joinTaskOptions = workflow && selectedTask
    ? buildJoinTaskOptions(workflow, selectedTask).map((reference) => ({
        taskReferenceName: reference,
        name: findTask(workflow.tasks, reference)?.name,
      }))
    : []
  const activeTab = state.matches('task') ? 'task' : state.matches('code') ? 'code' : 'workflow'
  const taskPath = state.context.selectedTaskPath ?? ''
  const editable = definitionState.matches('ready.lifecycle.editing')
  const pendingDrafts = draftProblems(context)
  const ownsDraft = pendingDrafts.some((problem) => problem.taskPath === taskPath)
  const otherTaskDraft = pendingDrafts.length > 0 && (activeTab !== 'task' || !ownsDraft)

  useEffect(() => {
    const focus = context.problemFocus
    if (!focus || focus.tab === 'code' || activeTab !== focus.tab) return
    const frame = window.requestAnimationFrame(() => {
      const fields = [...(contentRef.current?.querySelectorAll<HTMLElement>('[data-field-path]') ?? [])]
      const field = fields.filter((item) => focus.problem.path === item.dataset.fieldPath || focus.problem.path.startsWith(`${item.dataset.fieldPath}/`)).sort((a, b) => (b.dataset.fieldPath?.length ?? 0) - (a.dataset.fieldPath?.length ?? 0))[0]
      if (field) {
        field.scrollIntoView({ block: 'center' })
        field.querySelector<HTMLElement>('input, textarea, select, button[role="combobox"], [tabindex="0"]')?.focus()
      } else if (focus.problem.source !== 'draft') {
        definitionActor.send({ type: 'FOCUS_PROBLEM', problem: focus.problem, tab: 'code' })
      }
    })
    return () => window.cancelAnimationFrame(frame)
  }, [context.problemFocus, activeTab, definitionActor])

  const changeWorkflow = workflow
    ? (nextWorkflow: typeof workflow) => {
        actor.send({ type: 'WORKFLOW_CHANGED', workflow: nextWorkflow })
      }
    : undefined

  return (
    <>
      <div className="inspector-tabs">
        <button
          type="button"
          className="collapse-tab"
          title="收起属性面板"
          onClick={onCollapse}
        >
          <CaretDoubleRightIcon size={18} />
        </button>
        <button
          type="button"
          className={activeTab === 'workflow' ? 'active' : ''}
          onClick={() => actor.send({ type: 'CHANGE_TAB', tab: 'workflow' })}
        >
          工作流
        </button>
        <button
          type="button"
          className={activeTab === 'task' ? 'active' : ''}
          onClick={() => actor.send({ type: 'CHANGE_TAB', tab: 'task' })}
        >
          任务
        </button>
        <button
          type="button"
          className={activeTab === 'code' ? 'active' : ''}
          onClick={() => actor.send({ type: 'CHANGE_TAB', tab: 'code' })}
        >
          源码
        </button>
      </div>

      {context.sourceBlocked && activeTab !== 'code' ? <div className="workflow-draft-banner">源码尚未通过结构校验，编辑草稿已保留。<button type="button" onClick={() => actor.send({ type: 'CHANGE_TAB', tab: 'code' })}>修复源码</button></div> : null}
      {otherTaskDraft && activeTab !== 'code' ? <div className="workflow-draft-banner">任务中有未完成的草稿，修复后可编辑其他配置。<button type="button" onClick={() => definitionActor.send({ type: 'FOCUS_PROBLEM', problem: pendingDrafts[0] })}>返回草稿</button></div> : null}
      <div className="inspector-scroll" ref={contentRef}>
        <ValidationScope value={{ actor: definitionActor, basePath: activeTab === 'task' ? taskPath : '', taskReferenceName: selectedTask?.taskReferenceName, focus: context.problemFocus, readOnly: !editable || context.sourceBlocked || otherTaskDraft }}>
        <fieldset className="inspector-edit-region" disabled={!editable || context.sourceBlocked || otherTaskDraft} hidden={activeTab === 'code'}>
        {activeTab === 'workflow' && workflow && changeWorkflow ? (
          <WorkflowInspector workflow={workflow} variableOptions={variableOptions} onChange={changeWorkflow} />
        ) : null}

        {activeTab === 'task' ? (
          selectedTask ? (
            getTaskPlugin(selectedTask.type)?.inspectorLayout === 'bare' ? (
              <TaskInspector
                key={selectedTaskEditorKey}
                task={selectedTask}
                variableOptions={variableOptions}
                inputVariableOptions={inputVariableOptions}
                joinTaskOptions={joinTaskOptions}
                onChange={(task) => actor.send({ type: 'TASK_UPDATED', task })}
              />
            ) : (
              <div className="inspector-section task-panel-section">
                <TaskInspector
                  key={selectedTaskEditorKey}
                  task={selectedTask}
                  variableOptions={variableOptions}
                  inputVariableOptions={inputVariableOptions}
                  joinTaskOptions={joinTaskOptions}
                  onChange={(task) => actor.send({ type: 'TASK_UPDATED', task })}
                />
              </div>
            )
          ) : (
            <div className="inspector-section task-panel-section">
              <div className="inspector-empty-state">
                <strong>尚未选择任务</strong>
                <span>点击画布中的任务节点后，可在这里编辑任务属性。</span>
              </div>
            </div>
          )
        ) : null}

        </fieldset>
        </ValidationScope>
        {activeTab === 'code' ? <>
          {pendingDrafts.length ? <div className="workflow-draft-banner">任务参数中有未完成的草稿，修复后可编辑源码。<button type="button" onClick={() => definitionActor.send({ type: 'FOCUS_PROBLEM', problem: pendingDrafts[0] })}>返回任务</button></div> : null}
          <CodeInspector source={context.source} problems={context.problems} focus={context.problemFocus} readOnly={!editable || pendingDrafts.length > 0} onChange={(source) => definitionActor.send({ type: 'SOURCE_CHANGED', source })} />
        </> : null}
      </div>
    </>
  )
}

export function EditorPanel({
  actor,
  onCollapse,
}: {
  actor: WorkflowDefinitionActor
  onCollapse: () => void
}) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const { editorActor, validationActor } = state.context

  return (
    <aside className="workflow-inspector">
      {state.matches('ready') && editorActor ? <>
        <DerivedEditorContent actor={editorActor} definitionActor={actor} onCollapse={onCollapse} />
        {validationActor ? <ErrorInspector actor={actor} validationActor={validationActor} /> : null}
      </> : null}
    </aside>
  )
}
