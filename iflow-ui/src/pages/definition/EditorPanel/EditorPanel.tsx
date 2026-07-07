import { useSelector } from '@xstate/react'
import { CaretDoubleRight } from '@phosphor-icons/react'
import { TaskType } from '@/flow/taskType'
import {
  CodeInspector,
  TaskInspector,
  WorkflowInspector,
} from '@/pages/WorkflowDefinitionPage'
import type { ActorRefFrom } from 'xstate'
import {
  workflowEditorMachine,
  type WorkflowDefinitionActor,
} from '../state'

function DerivedEditorContent({
  actor,
  definitionActor,
}: {
  actor: ActorRefFrom<typeof workflowEditorMachine>
  definitionActor: WorkflowDefinitionActor
}) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const { workflow, selectedTask, selectedTaskEditorKey, variableOptions } = state.context
  const activeTab = state.matches('task') ? 'task' : state.matches('code') ? 'code' : 'workflow'

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
          onClick={() => definitionActor.send({ type: 'CLOSE_PANEL' })}
        >
          <CaretDoubleRight size={18} />
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

      <div className="inspector-scroll">
        {activeTab === 'workflow' && workflow && changeWorkflow ? (
          <WorkflowInspector workflow={workflow} variableOptions={variableOptions} onChange={changeWorkflow} />
        ) : null}

        {activeTab === 'task' ? (
          selectedTask ? (
            selectedTask.type === TaskType.INLINE
              || selectedTask.type === TaskType.HTTP
              || selectedTask.type === TaskType.PARAM_MAP
              || selectedTask.type === TaskType.SPI ? (
              <TaskInspector
                key={selectedTaskEditorKey}
                task={selectedTask}
                variableOptions={variableOptions}
                onChange={(task) => actor.send({ type: 'TASK_UPDATED', task })}
              />
            ) : (
              <div className="inspector-section task-panel-section">
                <TaskInspector
                  key={selectedTaskEditorKey}
                  task={selectedTask}
                  variableOptions={variableOptions}
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

        {activeTab === 'code' && workflow && changeWorkflow ? (
          <CodeInspector workflow={workflow} onChange={changeWorkflow} />
        ) : null}
      </div>
    </>
  )
}

export function EditorPanel({ actor }: { actor: WorkflowDefinitionActor }) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const { editorActor } = state.context

  return (
    <aside className="workflow-inspector">
      {editorActor ? <DerivedEditorContent actor={editorActor} definitionActor={actor} /> : null}
    </aside>
  )
}
