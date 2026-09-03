import { useSelector } from '@xstate/react'
import TwoPanesDivider from '@/components/ui/TwoPanesDivider'
import { EditorPanel } from './EditorPanel/EditorPanel'
import { GraphPanel } from './GraphPanel'
import type { WorkflowDefinitionActor } from './state'

export function WorkflowWorkspace({ actor }: { actor: WorkflowDefinitionActor }) {
  const state = useSelector(actor, (snapshot) => snapshot)
  const leftPanelExpanded = state.matches('ready.rightPanel.closed')
  const setLeftPanelExpanded = (expanded: boolean) => {
    actor.send({ type: expanded ? 'CLOSE_PANEL' : 'OPEN_PANEL' })
  }

  return (
    <TwoPanesDivider
      leftPanelContent={<GraphPanel actor={actor} />}
      rightPanelContent={(
        <EditorPanel
          actor={actor}
          onCollapse={() => setLeftPanelExpanded(true)}
        />
      )}
      leftPanelExpanded={leftPanelExpanded}
      setLeftPanelExpanded={setLeftPanelExpanded}
      rightPanelWidth={state.context.inspectorWidth}
      setRightPanelWidth={(width) => actor.send({ type: 'RESIZE_PANEL', width })}
      testId="workflow-definition-container"
    />
  )
}
