import { Button, Stack, type ButtonProps } from '@mui/material'
import { Fragment, type ReactNode } from 'react'

type SectionHeaderAction =
  | ({ key: string; label: string } & ButtonProps)
  | { key: string; customButtonElement: ReactNode }

/** Matches Conductor's array-based page header actions. */
export function SectionHeaderActions({ buttons }: { buttons: SectionHeaderAction[] }) {
  return (
    <Stack direction="row" gap={1.5} alignItems="center" flexWrap="wrap">
      {buttons.map((action) => {
        if ('customButtonElement' in action) {
          return <Fragment key={action.key}>{action.customButtonElement}</Fragment>
        }
        const { key, label, ...props } = action
        return <Button key={key} {...props}>{label}</Button>
      })}
    </Stack>
  )
}
