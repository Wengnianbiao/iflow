import type { Icon } from '@phosphor-icons/react'
import { ClockCounterClockwiseIcon, SlidersHorizontalIcon } from '@phosphor-icons/react'

export type SidebarNavItem = {
  label: string
  to: string
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
    icon: ClockCounterClockwiseIcon,
    items: [{ label: '工作流调度', to: '/executions' }],
  },
  {
    id: 'configuration',
    label: '配置管理',
    icon: SlidersHorizontalIcon,
    items: [{ label: '工作流配置', to: '/workflowDef', end: true }],
  },
]
