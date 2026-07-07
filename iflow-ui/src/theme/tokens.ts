export type ColorMode = 'light' | 'dark'

export type ThemeTokens = {
  mode: ColorMode
  colors: {
    primary: string
    primarySoft: string
    primaryMuted: string
    text: string
    textSecondary: string
    textMuted: string
    background: string
    surface: string
    surfaceMuted: string
    shell: string
    border: string
    danger: string
    dangerSoft: string
    success: string
    warning: string
  }
  fontFamily: string
}

export const lightTheme: ThemeTokens = {
  mode: 'light',
  colors: {
    primary: '#1976d2',
    primarySoft: '#e8f2ff',
    primaryMuted: '#f6f9fd',
    text: '#111111',
    textSecondary: '#333333',
    textMuted: '#777777',
    background: '#ffffff',
    surface: '#ffffff',
    surfaceMuted: '#f6f6f6',
    shell: '#eeeeee',
    border: '#dddddd',
    danger: '#c62828',
    dangerSoft: '#b42318',
    success: '#18b264',
    warning: '#eeaa00',
  },
  fontFamily:
    'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
}

export const darkTheme: ThemeTokens = {
  mode: 'dark',
  colors: {
    primary: '#64b5f6',
    primarySoft: '#1a3348',
    primaryMuted: '#152636',
    text: '#f5f5f5',
    textSecondary: '#d0d0d0',
    textMuted: '#9a9a9a',
    background: '#121212',
    surface: '#1e1e1e',
    surfaceMuted: '#2a2a2a',
    shell: '#0f0f0f',
    border: '#3a3a3a',
    danger: '#ef5350',
    dangerSoft: '#e57373',
    success: '#66bb6a',
    warning: '#ffca28',
  },
  fontFamily: lightTheme.fontFamily,
}

export function getTheme(mode: ColorMode): ThemeTokens {
  return mode === 'dark' ? darkTheme : lightTheme
}

export function applyThemeCssVariables(theme: ThemeTokens, target: HTMLElement = document.documentElement) {
  const { colors } = theme
  target.style.setProperty('--iflow-color-primary', colors.primary)
  target.style.setProperty('--iflow-color-primary-soft', colors.primarySoft)
  target.style.setProperty('--iflow-color-primary-muted', colors.primaryMuted)
  target.style.setProperty('--iflow-color-text', colors.text)
  target.style.setProperty('--iflow-color-text-secondary', colors.textSecondary)
  target.style.setProperty('--iflow-color-text-muted', colors.textMuted)
  target.style.setProperty('--iflow-color-background', colors.background)
  target.style.setProperty('--iflow-color-surface', colors.surface)
  target.style.setProperty('--iflow-color-surface-muted', colors.surfaceMuted)
  target.style.setProperty('--iflow-color-shell', colors.shell)
  target.style.setProperty('--iflow-color-border', colors.border)
  target.style.setProperty('--iflow-color-danger', colors.danger)
  target.style.setProperty('--iflow-color-danger-soft', colors.dangerSoft)
  target.style.setProperty('--iflow-color-success', colors.success)
  target.style.setProperty('--iflow-color-warning', colors.warning)
  target.style.setProperty('--iflow-font-family', theme.fontFamily)
  target.dataset.colorMode = theme.mode
}
