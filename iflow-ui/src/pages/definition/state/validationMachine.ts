import { assign, createMachine } from 'xstate'
import type { WorkflowProblem } from '../validation/types'

export const workflowValidationMachine = createMachine<
  { problems: WorkflowProblem[] },
  { type: 'SYNC'; problems: WorkflowProblem[] } | { type: 'TOGGLE' } | { type: 'EXPAND' } | { type: 'RESET' }
>({
  predictableActionArguments: true,
  id: 'workflowValidation',
  initial: 'collapsed',
  context: { problems: [] },
  on: {
    SYNC: { actions: assign({ problems: (_context, event) => event.type === 'SYNC' ? event.problems : [] }) },
    EXPAND: '.expanded',
    RESET: { target: '.collapsed', actions: assign({ problems: [] }) },
  },
  states: {
    collapsed: { on: { TOGGLE: 'expanded' } },
    expanded: { on: { TOGGLE: 'collapsed' } },
  },
})
