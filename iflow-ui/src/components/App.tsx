import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { AppSidebar } from './layout/AppSidebar'
import styles from './App.module.css'

export function App() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)

  return (
    <div className={`${styles.shell} ${sidebarCollapsed ? styles.collapsed : ''}`}>
      <AppSidebar collapsed={sidebarCollapsed} onCollapsedChange={setSidebarCollapsed} />
      <section className={styles.content}>
        <Outlet />
      </section>
    </div>
  )
}
