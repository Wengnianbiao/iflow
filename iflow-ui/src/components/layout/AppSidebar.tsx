import { useState } from 'react'
import { CaretDownIcon, CaretLeftIcon, CaretRightIcon } from '@phosphor-icons/react'
import { NavLink } from 'react-router-dom'
import {
  sidebarNavGroups,
  type SidebarNavGroupId,
} from './sidebarItems'
import styles from './AppSidebar.module.css'

type AppSidebarProps = {
  collapsed: boolean
  onCollapsedChange: (collapsed: boolean) => void
}

export function AppSidebar({ collapsed, onCollapsedChange }: AppSidebarProps) {
  const [openGroups, setOpenGroups] = useState<Record<SidebarNavGroupId, boolean>>({
    scheduling: true,
    configuration: true,
  })

  const toggleGroup = (groupId: SidebarNavGroupId) => {
    if (collapsed) {
      onCollapsedChange(false)
      setOpenGroups((current) => ({ ...current, [groupId]: true }))
      return
    }

    setOpenGroups((current) => ({ ...current, [groupId]: !current[groupId] }))
  }

  return (
    <aside className={`${styles.sidebar} ${collapsed ? styles.collapsed : ''}`}>
      <div className={styles.brandRow}>
        <img className={styles.brandMark} src="/favicon.svg?v=2" alt="" aria-hidden="true" />
        <div className={styles.brandName}>接口工作流平台</div>
        <button
          type="button"
          className={styles.collapseButton}
          aria-label={collapsed ? '展开侧栏' : '收起侧栏'}
          title={collapsed ? '展开侧栏' : '收起侧栏'}
          onClick={() => onCollapsedChange(!collapsed)}
        >
          {collapsed ? <CaretRightIcon size={14} weight="bold" /> : <CaretLeftIcon size={14} weight="bold" />}
        </button>
      </div>

      <nav className={styles.navigation} aria-label="主导航">
        {sidebarNavGroups.map((group) => {
          const Icon = group.icon
          const isOpen = openGroups[group.id]

          return (
            <div
              key={group.id}
              className={`${styles.group} ${isOpen ? styles.open : ''}`}
            >
              <button
                type="button"
                className={styles.groupTitle}
                aria-expanded={isOpen}
                title={collapsed ? group.label : undefined}
                onClick={() => toggleGroup(group.id)}
              >
                <span className={styles.groupIcon} aria-hidden="true">
                  <Icon size={22} weight="duotone" />
                </span>
                <span>{group.label}</span>
                <CaretDownIcon className={styles.groupCaret} size={14} weight="bold" />
              </button>
              <div className={styles.groupItems}>
                <div className={styles.groupItemsInner}>
                  {group.items.map((item) => {
                    const ItemIcon = item.icon

                    return (
                      <NavLink
                        key={item.to}
                        to={item.to}
                        end={item.end}
                        className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}
                      >
                        <ItemIcon className={styles.navIcon} size={18} weight="duotone" aria-hidden="true" />
                        <span>{item.label}</span>
                      </NavLink>
                    )
                  })}
                </div>
              </div>
            </div>
          )
        })}
      </nav>
    </aside>
  )
}
