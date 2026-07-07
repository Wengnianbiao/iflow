import { forwardRef, type CSSProperties, type ForwardedRef, type ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { ArrowSquareOutIcon } from '@phosphor-icons/react'

type NavLinkProps = {
  path: string
  newTab?: boolean
  children: ReactNode
  id?: string
  className?: string
  style?: CSSProperties
}

/**
 * App-internal navigation link, aligned with Conductor ui-next NavLink.
 * iFlow has no multi-stack env, so path is used as-is.
 */
export const NavLink = forwardRef(function NavLink(
  props: NavLinkProps,
  ref: ForwardedRef<HTMLAnchorElement>,
) {
  const { path, newTab, children, ...rest } = props

  if (!newTab) {
    return (
      <RouterLink ref={ref} to={path} {...rest}>
        {children}
      </RouterLink>
    )
  }

  return (
    <a
      ref={ref}
      href={path}
      target="_blank"
      rel="noreferrer"
      style={{ display: 'flex', alignItems: 'center', ...rest.style }}
      className={rest.className}
      id={rest.id}
    >
      {children}
      &nbsp;
      <ArrowSquareOutIcon />
    </a>
  )
})
