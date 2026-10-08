import type { Icon } from '@phosphor-icons/react'
import { ClockCountdownIcon, FlowArrowIcon, PlayCircleIcon, TreeStructureIcon } from '@phosphor-icons/react'

export type SidebarNavItem = {
  label: string
  to: string
  icon: Icon
  end?: boolean
}

export type SidebarNavGroupId = 'scheduling' | 'configuration'

export type SidebarNavGroup = {
  id: SidebarNavGroupId
  label: string
  icon: Icon
  items: SidebarNavItem[]
}

export const sidebarNavGroups: SidebarNavGroup[] = [
  {
    id: 'scheduling',
    label: '调度日志',
    icon: ClockCountdownIcon,
    items: [{ label: '工作流调度', to: '/executions', icon: PlayCircleIcon }],
  },
  {
    id: 'configuration',
    label: '配置管理',
    icon: TreeStructureIcon,
    items: [{ label: '工作流配置', to: '/workflowDef', icon: FlowArrowIcon, end: true }],
  },
]
