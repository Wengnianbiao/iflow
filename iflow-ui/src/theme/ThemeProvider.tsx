import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
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

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider')
  }
  return context
}
