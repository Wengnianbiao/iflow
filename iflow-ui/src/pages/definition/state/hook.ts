import { useEffect } from 'react'
import { useInterpret } from '@xstate/react'
import { useNavigate } from 'react-router-dom'
import {
  deleteWorkflowDefinition,
  getWorkflowDefinition,
  listWorkflowVersions,
  saveWorkflowDefinition,
} from '@/api/workflowDefinition'
import { useMessageContext } from '@/components/providers/messageContext/MessageContext'
import type { WorkflowDef } from '@/types/workflow'
import { readWorkflowDraft } from '@/utils/workflowDraft'
import { workflowDefinitionMachine } from './machine'
import type {
  LoadedWorkflow,
  WorkflowDefinitionMachineContext,
  WorkflowRouteAttributes,
} from './types'

function editorUrl(name: string, version?: number) {
  const query = new URLSearchParams({ name })
  if (version != null) query.set('version', String(version))
  return `/workflowDef/edit?${query.toString()}`
}

function createWorkflow(attributes: WorkflowRouteAttributes): WorkflowDef {
  return {
    name: attributes.workflowName,
    version: attributes.workflowVersion ?? 1,
    description: '',
    inputParameters: [],
    outputParameters: {},
    workflowStatusListenerEnabled: false,
    timeoutSeconds: 0,
    timeoutPolicy: 'ALERT_ONLY',
    tasks: [],
  }
}

async function loadWorkflow(attributes: WorkflowRouteAttributes): Promise<LoadedWorkflow> {
  if (attributes.isNewWorkflow) {
    const baseline = createWorkflow(attributes)
    const workflow = readWorkflowDraft(attributes.draftId) ?? baseline
    return {
      workflow,
      baseline,
      versions: [workflow.version],
    }
  }

  const [workflow, versions] = await Promise.all([
    getWorkflowDefinition(attributes.workflowName, attributes.workflowVersion),
    listWorkflowVersions(attributes.workflowName).catch(() => (
      attributes.workflowVersion == null ? [] : [attributes.workflowVersion]
    )),
  ])

  return {
    workflow,
    versions: [...new Set([...versions, workflow.version])].sort((a, b) => a - b),
  }
}

export function useWorkflowDefinition(attributes: WorkflowRouteAttributes) {
  const navigate = useNavigate()
  const { setMessage } = useMessageContext()
  const actor = useInterpret(workflowDefinitionMachine, {
    services: {
      loadWorkflowDefinition: (context) => loadWorkflow({
        workflowName: context.workflowName,
        workflowVersion: context.workflowVersion,
        isNewWorkflow: context.isNewWorkflow,
        draftId: context.draftId,
      }),
      saveWorkflowDefinition: (context) => {
        if (!context.workflow) throw new Error('没有可保存的工作流')
        return saveWorkflowDefinition(context.workflow)
      },
      deleteWorkflowDefinition: (context) => {
        if (!context.workflow) throw new Error('没有可删除的工作流')
        return deleteWorkflowDefinition(context.workflow.name, context.workflow.version)
      },
    },
    actions: {
      notifyWorkflowSaved: () => {
        setMessage({ text: '工作流已保存', severity: 'success' })
      },
      notifyWorkflowReset: () => {
        setMessage({ text: '已恢复为上次保存的内容', severity: 'success' })
      },
      notifyWorkflowDeleted: () => {
        setMessage({ text: '工作流版本已删除', severity: 'success' })
      },
      navigateToPendingVersion: (context: WorkflowDefinitionMachineContext) => {
        if (context.pendingVersion == null) return
        navigate(
          context.pendingVersion === 'latest'
            ? editorUrl(context.workflowName)
            : editorUrl(context.workflowName, context.pendingVersion),
        )
      },
      navigateToSavedWorkflow: (context: WorkflowDefinitionMachineContext) => {
        if (!context.workflow) return
        navigate(editorUrl(context.workflow.name, context.workflow.version), { replace: true })
      },
      navigateToDefinitionList: () => {
        navigate('/workflowDef')
      },
    },
  })

  useEffect(() => {
    actor.send({
      type: 'INITIALIZE',
      ...attributes,
    })
  }, [
    actor,
    attributes.isNewWorkflow,
    attributes.draftId,
    attributes.workflowName,
    attributes.workflowVersion,
  ])

  return actor
}
