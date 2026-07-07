import type { ReactNode } from 'react'
import styles from './SectionContainer.module.css'

type SectionContainerProps = {
  children?: ReactNode
  header?: ReactNode
}

export function SectionContainer({
  children,
  header = null,
}: SectionContainerProps) {
  return (
    <div className={styles.root}>
      {header ? <div className={styles.stickyHeader}>{header}</div> : null}
      <main className={styles.container} id="section-container">
        {children}
      </main>
    </div>
  )
}
