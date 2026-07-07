import { useState } from 'react'
import { CaretDown, CaretLeft, CaretRight } from '@phosphor-icons/react'
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
        <div className={styles.brandMark}>C</div>
        <div className={styles.brandName}>接口工作流</div>
        <button
          type="button"
          className={styles.collapseButton}
          aria-label={collapsed ? '展开侧栏' : '收起侧栏'}
          title={collapsed ? '展开侧栏' : '收起侧栏'}
          onClick={() => onCollapsedChange(!collapsed)}
        >
          {collapsed ? <CaretRight size={14} weight="bold" /> : <CaretLeft size={14} weight="bold" />}
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
                  <Icon size={19} weight="regular" />
                </span>
                <span>{group.label}</span>
                <CaretDown className={styles.groupCaret} size={14} weight="bold" />
              </button>
              <div className={styles.groupItems}>
                <div className={styles.groupItemsInner}>
                  {group.items.map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end={item.end}
                      className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}
                    >
                      {item.label}
                    </NavLink>
                  ))}
                </div>
              </div>
            </div>
          )
        })}
      </nav>
    </aside>
  )
}
