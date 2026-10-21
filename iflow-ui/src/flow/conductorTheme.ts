import { getTaskAppearance } from '@/tasks'

export const TaskStatus = {
  COMPLETED: 'COMPLETED',
  COMPLETED_WITH_ERRORS: 'COMPLETED_WITH_ERRORS',
  CANCELED: 'CANCELED',
  FAILED: 'FAILED',
  FAILED_WITH_TERMINAL_ERROR: 'FAILED_WITH_TERMINAL_ERROR',
  TIMED_OUT: 'TIMED_OUT',
  SCHEDULED: 'SCHEDULED',
  IN_PROGRESS: 'IN_PROGRESS',
  SKIPPED: 'SKIPPED',
  PENDING: 'PENDING',
  NULL: 'NULL',
} as const

export const conductorTheme = {
  taskStatusOutline: {
    [TaskStatus.COMPLETED]: '#18b264',
    [TaskStatus.COMPLETED_WITH_ERRORS]: '#EEAA00',
    [TaskStatus.CANCELED]: '#fba404',
    [TaskStatus.FAILED]: '#DD2222',
    [TaskStatus.FAILED_WITH_TERMINAL_ERROR]: '#DD2222',
    [TaskStatus.TIMED_OUT]: '#DD2222',
    [TaskStatus.SCHEDULED]: '#999999',
    [TaskStatus.IN_PROGRESS]: '#999999',
    [TaskStatus.SKIPPED]: '#F5BF42',
    [TaskStatus.PENDING]: 'transparent',
    [TaskStatus.NULL]: 'transparent',
  },
  taskCard: {
    selected: {
      outlineColor: '#3388dd',
      boxShadow: 'none',
    },
    operators: {
      background: '#205668',
      text: 'white',
    },
    systemTasks: {
      background: 'white',
      color: '#111111',
    },
    cardLabel: {
      background: '#dddddd',
      color: 'black',
    },
    deleteButton: {
      iconColor: '#DD2222',
      background: '#f0f0f0',
    },
    switchAdd: {
      iconColor: 'black',
      background: '#f9f53d',
    },
  },
  terminalTask: {
    color: '#111111',
    background: '#ffffff',
    border: '5px solid rgb(114 164 180)',
  },
  decisionOperator: {
    caseLabel: {
      defaultCaseBackground: 'rgb(225 243 255)',
      background: 'rgb(225 243 255)',
    },
  },
  edges: {
    default: {
      stroke: '#757575',
      strokeWidth: 1,
    },
    completed: {
      stroke: '#18b264',
      strokeWidth: 2,
    },
  },
}

export function getCardVariant(type: string, status?: string, selected?: boolean) {
  const outlineColor = selected
    ? conductorTheme.taskCard.selected.outlineColor
    : conductorTheme.taskStatusOutline[(status as keyof typeof conductorTheme.taskStatusOutline) ?? TaskStatus.NULL]

  const appearance = getTaskAppearance(type)
  const isOperator = appearance.cardVariant === 'operator'

  let cardStyles: Record<string, string> = isOperator
    ? {
        backgroundColor: conductorTheme.taskCard.operators.background,
        border: outlineColor ? `3px solid ${outlineColor}` : '3px solid transparent',
        color: conductorTheme.taskCard.operators.text,
        borderRadius: '10px',
      }
    : {
        backgroundColor: conductorTheme.taskCard.systemTasks.background,
        border: outlineColor ? `3px solid ${outlineColor}` : '3px solid transparent',
        color: conductorTheme.taskCard.systemTasks.color,
        borderRadius: '10px',
      }

  if ([TaskStatus.FAILED, TaskStatus.SKIPPED, TaskStatus.CANCELED, TaskStatus.TIMED_OUT].includes(status as never)) {
    const stripe = isOperator ? 'rgba(90, 0, 0, .25)' : 'rgba(220, 110, 110, .15)'
    cardStyles = {
      ...cardStyles,
      backgroundImage: `linear-gradient(135deg, rgba(0,0,0,0) 25%, ${stripe} 25%, ${stripe} 50%, rgba(0,0,0,0) 50%, rgba(0,0,0,0) 75%, ${stripe} 75%, ${stripe} 100%)`,
      backgroundSize: '56.57px 56.57px',
    }
  }

  const boxShadows = appearance.shadow ? ['0 2px 20px rgba(0,0,0,.4)'] : []
  if (selected) boxShadows.push(conductorTheme.taskCard.selected.boxShadow)

  return {
    ...cardStyles,
    boxShadow: boxShadows.join(', '),
  }
}
