import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { CssBaseline } from '@mui/material'
import {
  ThemeProvider as MuiThemeProvider,
  createTheme as createMuiTheme,
} from '@mui/material/styles'
import {
  applyThemeCssVariables,
  getTheme,
  type ColorMode,
  type ThemeTokens,
} from './tokens'

const COLOR_MODE_STORAGE_KEY = 'iflow-color-mode'

type ThemeContextValue = {
  theme: ThemeTokens
  mode: ColorMode
  setMode: (mode: ColorMode) => void
  toggleColorMode: () => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function readStoredMode(): ColorMode {
  try {
    const stored = window.localStorage.getItem(COLOR_MODE_STORAGE_KEY)
    if (stored === 'light' || stored === 'dark') return stored
  } catch {
    // Ignore storage access errors (private mode, etc.).
  }
  return 'light'
}

export function ThemeProvider({
  children,
  defaultMode = 'light',
}: {
  children: ReactNode
  defaultMode?: ColorMode
}) {
  const [mode, setModeState] = useState<ColorMode>(() => {
    if (typeof window === 'undefined') return defaultMode
    return readStoredMode()
  })

  const theme = useMemo(() => getTheme(mode), [mode])
  const muiTheme = useMemo(
    () => createMuiTheme({
      palette: {
        mode,
        primary: { main: theme.colors.primary },
        error: { main: theme.colors.danger },
        success: { main: theme.colors.success },
        warning: { main: theme.colors.warning },
        background: {
          default: theme.colors.background,
          paper: theme.colors.surface,
        },
        text: {
          primary: theme.colors.text,
          secondary: theme.colors.textSecondary,
        },
        divider: theme.colors.border,
      },
      typography: {
        fontFamily: theme.fontFamily,
        fontSize: 13,
        button: { textTransform: 'none', fontWeight: 600 },
      },
      shape: { borderRadius: 6 },
      components: {
        MuiButton: {
          defaultProps: { disableElevation: true },
        },
        MuiDialogTitle: {
          styleOverrides: { root: { fontSize: 18, fontWeight: 700 } },
        },
        MuiTableCell: {
          styleOverrides: {
            head: {
              color: theme.colors.textMuted,
              backgroundColor: theme.colors.surfaceMuted,
              fontSize: 12,
              fontWeight: 700,
            },
            body: {
              color: theme.colors.textSecondary,
              fontSize: 13,
            },
          },
        },
      },
    }),
    [mode, theme],
  )

  useEffect(() => {
    applyThemeCssVariables(theme)
    try {
      window.localStorage.setItem(COLOR_MODE_STORAGE_KEY, mode)
    } catch {
      // Ignore storage write errors.
    }
  }, [mode, theme])

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme,
      mode,
      setMode: setModeState,
      toggleColorMode: () => setModeState((prev) => (prev === 'light' ? 'dark' : 'light')),
    }),
    [mode, theme],
  )

  return (
    <ThemeContext.Provider value={value}>
      <MuiThemeProvider theme={muiTheme}>
        <CssBaseline />
        {children}
      </MuiThemeProvider>
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider')
  }
  return context
}
