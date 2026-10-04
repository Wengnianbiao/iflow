import type { WorkflowTask } from '@/types/workflow'
import { spiJavaTemplate } from '@/tasks/templates'
import {
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  prettyJson,
  valueText,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'
import { isRecord } from './formUtils'
import { ValidationField, useTaskFieldDraft } from '../../../validation/ValidationScope'
import { jsonErrorMessage } from '../../../validation/jsonErrorMessage'

function spiInputSource(value: unknown) {
  return typeof value === 'string' ? value : prettyJson(isRecord(value) ? value : {})
}

export function SPITaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const inputParameters = task.inputParameters ?? {}
  const remoteExtension = typeof inputParameters.remoteExtension === 'string'
    ? inputParameters.remoteExtension
    : valueText(inputParameters.remoteExtension)
  const extensionMode = inputParameters.extensionMode === 'PROJECT' ? 'PROJECT' : 'ONLINE'
  const className = typeof inputParameters.className === 'string' ? inputParameters.className : ''
  const spiDraft = useTaskFieldDraft('/inputParameters/spiInput', spiInputSource(inputParameters.spiInput))
  const javaSource = (
    typeof inputParameters.sourceCode === 'string'
      ? inputParameters.sourceCode
      : spiJavaTemplate(className || 'com.helianhealth.iflow.extension.CustomSpiTaskExtension')
  )

  const updateParameters = (changes: Record<string, unknown>) => {
    onChange({
      ...task,
      inputParameters: {
        ...inputParameters,
        ...changes,
      },
    })
  }

  const updateSpiInput = (nextSource: string) => {
    const expression = nextSource.trim()
    if (expression.startsWith('${') && expression.endsWith('}')) {
      updateParameters({ spiInput: expression })
      spiDraft.applied(nextSource)
      return
    }

    try {
      const parsed = JSON.parse(nextSource) as unknown
      if (!isRecord(parsed)) throw new Error('spiInput 必须是 JSON Map 或返回 Map 的 ${...} 表达式')
      updateParameters({ spiInput: parsed })
      spiDraft.applied(nextSource)
    } catch (error) {
      spiDraft.invalid(nextSource, jsonErrorMessage(error, 'spiInput'))
    }
  }

  const updateJavaSource = (value: string) => {
    updateParameters({ sourceCode: value })
  }

  return (
    <div className="workflow-form-panel inline-task-inspector spi-task-inspector">
      <TaskPanelHeader task={task} label="SPI 扩展" onChange={onChange} />

      <WorkflowCollapsibleSection title="SPI 实现">
        <ValidationField path="/inputParameters/remoteExtension"><label className="inspector-field">
          <span>扩展名称（remoteExtension） <strong>*</strong></span>
          <VariableExpressionInput
            value={remoteExtension}
            options={variableOptions}
            onChange={(value) => updateParameters({ remoteExtension: value })}
          />
          <small>用于注入和路由 ExtensionLoader；也支持返回扩展名的 ${'{...}'} 表达式。</small>
        </label></ValidationField>

        <div className="switch-evaluator-heading spi-extension-mode-heading">
          <span>扩展方式</span>
          <div className="switch-evaluator-options" role="group" aria-label="SPI 扩展方式">
            <button
              type="button"
              className={extensionMode === 'ONLINE' ? 'active' : ''}
              onClick={() => updateParameters({ extensionMode: 'ONLINE', sourceCode: javaSource })}
            >
              在线代码
            </button>
            <button
              type="button"
              className={extensionMode === 'PROJECT' ? 'active' : ''}
              onClick={() => updateParameters({ extensionMode: 'PROJECT', sourceCode: undefined })}
            >
              项目实现
            </button>
          </div>
        </div>

        <ValidationField path="/inputParameters/className"><label className="inspector-field">
          <span>实现类全名 {extensionMode === 'ONLINE' ? <strong>*</strong> : null}</span>
          <input
            value={className}
            spellCheck={false}
            placeholder="com.example.CustomRemoteInvoker"
            onChange={(event) => updateParameters({ className: event.target.value })}
          />
          <small>
            {extensionMode === 'ONLINE'
              ? '必须与下面 Java 源码中的 package 和 public 类名一致。'
              : '可留空以直接调用项目内已注册的同名扩展；填写后会从 ClassPath 注入。'}
          </small>
        </label></ValidationField>

        {extensionMode === 'ONLINE' ? (
          <ValidationField path="/inputParameters/sourceCode"><InlineScriptEditor
            value={javaSource}
            onChange={updateJavaSource}
            minHeight={440}
            language="java"
            languageLabel="JAVA 17"
            codeLabel="Java Source"
            modalTitle="SPI Java Source Editor"
          /></ValidationField>
        ) : null}
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="SPI 入参">
        <ValidationField path="/inputParameters/spiInput"><InlineScriptEditor
          value={spiDraft.value}
          onChange={updateSpiInput}
          minHeight={220}
          language="json"
          languageLabel="JSON / EXPRESSION"
          codeLabel="spiInput"
          modalTitle="SPI Input Editor"
        /></ValidationField>
        {!spiDraft.error ? <p className="field-help">输入 JSON Map，或直接使用返回 Map 的表达式，例如 <code>${'{workflow.input}'}</code>。</p> : null}
      </WorkflowCollapsibleSection>
    </div>
  )
}
