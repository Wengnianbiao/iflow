import { useEffect, useId, useRef } from 'react'
import { CodeIcon, PackageIcon, PlusCircleIcon } from '@phosphor-icons/react'
import type { WorkflowTask } from '@/types/workflow'
import { spiJavaTemplate } from '@/tasks/templates'
import {
  ParameterRows,
  VariableExpressionInput,
  WorkflowCollapsibleSection,
  prettyJson,
  valueText,
} from '../../InspectorFields'
import { InlineScriptEditor } from './InlineScriptEditor'
import { TaskPanelHeader } from './TaskPanelHeader'
import { isRecord } from './formUtils'
import { templateParameterText } from './templateParameters'
import { applySpiParameterDrafts, readSpiParameterDrafts, type SpiParameterDraft } from './spiInputParameters'
import { ValidationField, useTaskFieldDraft } from '../../../validation/ValidationScope'

function spiInputSource(value: unknown) {
  return typeof value === 'string' ? value : prettyJson(isRecord(value) ? value : {})
}

export function SPITaskForm({ task, variableOptions, onChange }: { task: WorkflowTask; variableOptions: string[]; onChange: (task: WorkflowTask) => void }) {
  const modeId = useId()
  const inputModeId = useId()
  const inputParameters = task.inputParameters ?? {}
  const remoteExtension = typeof inputParameters.remoteExtension === 'string'
    ? inputParameters.remoteExtension
    : valueText(inputParameters.remoteExtension)
  const extensionMode = inputParameters.extensionMode === 'PROJECT' ? 'PROJECT' : 'ONLINE'
  const className = typeof inputParameters.className === 'string' ? inputParameters.className : ''
  const spiDraft = useTaskFieldDraft('/inputParameters/spiInput', spiInputSource(inputParameters.spiInput))
  const spiInput = inputParameters.spiInput
  const inputMode = typeof spiInput === 'string' ? 'expression' : 'parameters'
  const parameters = isRecord(spiInput) ? spiInput : {}
  const parameterEntries = Object.entries(parameters)
  const parameterDrafts = readSpiParameterDrafts(spiDraft.value, spiDraft.error)
  const parameterRows = parameterEntries.map(([key, value]) => {
    const draft = parameterDrafts.find((draft) => draft.originalKey === key)
    return draft ? { key: draft.key, value: draft.value } : { key, value: templateParameterText(value) }
  })
  const previousParameters = useRef(parameters)
  const previousExpression = useRef(typeof spiInput === 'string' ? spiInput : '${workflow.input}')

  useEffect(() => {
    if (isRecord(spiInput)) previousParameters.current = spiInput
    else if (typeof spiInput === 'string') previousExpression.current = spiInput
  }, [spiInput])
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

  const updateSpiInput = (value: Record<string, unknown> | string) => {
    spiDraft.clear()
    updateParameters({ spiInput: value })
  }

  const changeInputMode = (nextMode: 'parameters' | 'expression') => {
    if (nextMode === inputMode) return
    updateSpiInput(nextMode === 'parameters' ? previousParameters.current : previousExpression.current)
  }

  const updateInputExpression = (value: string) => {
    if (/^\$\{[\s\S]+\}$/.test(value.trim())) updateSpiInput(value)
    else spiDraft.invalid(value, '请填写返回对象的 ${...} 表达式')
  }

  const commitParameters = (parameters: Record<string, unknown>, drafts: SpiParameterDraft[]) => {
    const result = applySpiParameterDrafts(parameters, drafts)
    if (result.message) spiDraft.invalid(JSON.stringify({ kind: 'spi-parameters', rows: result.drafts }), result.message)
    else spiDraft.clear()
    updateParameters({ spiInput: result.parameters })
  }

  const updateParameter = (index: number, next: { key: string; value: string }) => {
    const originalKey = parameterEntries[index][0]
    const drafts = parameterDrafts.filter((draft) => draft.originalKey !== originalKey)
    commitParameters(parameters, [...drafts, { originalKey, ...next }])
  }

  const deleteParameter = (index: number) => {
    const spiInput = Object.fromEntries(parameterEntries.filter((_, row) => row !== index))
    commitParameters(spiInput, parameterDrafts)
  }

  const addParameter = () => {
    let index = parameterEntries.length + 1
    let key = `parameter_${index}`
    while (Object.prototype.hasOwnProperty.call(parameters, key)) key = `parameter_${++index}`
    updateSpiInput({ ...parameters, [key]: '' })
  }

  const updateJavaSource = (value: string) => {
    updateParameters({ sourceCode: value })
  }

  return (
    <div className="workflow-form-panel inline-task-inspector spi-task-inspector">
      <TaskPanelHeader task={task} label="SPI 扩展" onChange={onChange} />

      <WorkflowCollapsibleSection title="SPI 入参">
        <div className="spi-input-mode-heading">
          <div className="switch-evaluator-options" role="radiogroup" aria-label="SPI 入参方式">
            <label>
              <input type="radio" name={inputModeId} checked={inputMode === 'parameters'} onChange={() => changeInputMode('parameters')} />
              <span>键值对</span>
            </label>
            <label>
              <input type="radio" name={inputModeId} checked={inputMode === 'expression'} onChange={() => changeInputMode('expression')} />
              <span>表达式</span>
            </label>
          </div>
          {inputMode === 'parameters' ? <span className="spi-input-count">{parameterEntries.length} 个参数</span> : null}
        </div>
        <ValidationField path="/inputParameters/spiInput">
          {inputMode === 'expression' ? (
            <label className="inspector-field">
              <span>入参表达式 <strong>*</strong></span>
              <VariableExpressionInput value={spiDraft.value} options={variableOptions} onChange={updateInputExpression} />
            </label>
          ) : (
            <ParameterRows
              items={parameterRows}
              keyLabel="参数名"
              valueLabel="参数值"
              variableOptions={variableOptions}
              onChange={updateParameter}
              onDelete={deleteParameter}
            />
          )}
        </ValidationField>
        {inputMode === 'parameters' ? (
          <>
            <button type="button" className="add-parameter-button spi-add-parameter" disabled={parameterDrafts.length > 0} onClick={addParameter}>
              <PlusCircleIcon size={15} />添加参数
            </button>
            <p className="field-help">参数值支持 <code>${'{...}'}</code> 表达式，也可填写 JSON 对象、数组、数字、布尔值或 null。</p>
          </>
        ) : <p className="field-help">表达式应返回对象，例如 <code>${'{workflow.input}'}</code> 或 <code>${'{prepare.output}'}</code>。</p>}
      </WorkflowCollapsibleSection>

      <WorkflowCollapsibleSection title="SPI 实现">
        <fieldset className="spi-extension-mode">
          <legend>扩展方式</legend>
          <div className="spi-extension-mode-options">
            <label className={`spi-mode-option ${extensionMode === 'ONLINE' ? 'selected' : ''}`}>
              <input
                type="radio"
                name={modeId}
                value="ONLINE"
                checked={extensionMode === 'ONLINE'}
                onChange={() => updateParameters({ extensionMode: 'ONLINE', sourceCode: javaSource })}
              />
              <CodeIcon size={20} aria-hidden="true" />
              <span><strong>在线代码</strong><small>在当前节点编写 Java 源码</small></span>
            </label>
            <label className={`spi-mode-option ${extensionMode === 'PROJECT' ? 'selected' : ''}`}>
              <input
                type="radio"
                name={modeId}
                value="PROJECT"
                checked={extensionMode === 'PROJECT'}
                onChange={() => updateParameters({ extensionMode: 'PROJECT', sourceCode: undefined })}
              />
              <PackageIcon size={20} aria-hidden="true" />
              <span><strong>项目实现</strong><small>调用项目中已有的 SPI 扩展</small></span>
            </label>
          </div>
        </fieldset>

        <ValidationField path="/inputParameters/remoteExtension"><label className="inspector-field">
          <span>扩展名称 <strong>*</strong></span>
          <VariableExpressionInput
            value={remoteExtension}
            options={variableOptions}
            onChange={(value) => updateParameters({ remoteExtension: value })}
          />
          <small>通过名称匹配 SPI 扩展，也支持 ${'{...}'} 表达式。</small>
        </label></ValidationField>

        <ValidationField path="/inputParameters/className"><label className="inspector-field">
          <span>实现类全名 {extensionMode === 'ONLINE' ? <strong>*</strong> : '（可选）'}</span>
          <input
            value={className}
            spellCheck={false}
            placeholder="com.example.CustomRemoteInvoker"
            onChange={(event) => updateParameters({ className: event.target.value })}
          />
          <small>
            {extensionMode === 'ONLINE'
              ? '必须与下面 Java 源码中的 package 和 public 类名一致。'
              : '留空时调用已注册的同名扩展；填写后加载指定实现类。'}
          </small>
        </label></ValidationField>

        {extensionMode === 'ONLINE' ? (
          <ValidationField path="/inputParameters/sourceCode"><InlineScriptEditor
            value={javaSource}
            onChange={updateJavaSource}
            minHeight={340}
            autoSize={false}
            showToolbar
            language="java"
            languageLabel="JAVA 17"
            codeLabel="Java 源码"
            modalTitle="SPI Java 源码编辑器"
          /></ValidationField>
        ) : null}
      </WorkflowCollapsibleSection>
    </div>
  )
}
