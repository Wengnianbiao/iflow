/** Load the browser formatter only when JavaScript formatting is requested. */
export async function formatJavaScriptSource(source: string, tabSize = 2, insertSpaces = true): Promise<string> {
  if (!source.trim()) return source
  const [prettier, babel, estree] = await Promise.all([
    import('prettier/standalone'),
    import('prettier/plugins/babel'),
    import('prettier/plugins/estree'),
  ])
  const formatted = await prettier.format(source, {
    parser: 'babel',
    plugins: [babel, estree],
    tabWidth: tabSize,
    useTabs: !insertSpaces,
    printWidth: 80,
    embeddedLanguageFormatting: 'off',
  })
  return formatted.trimEnd()
}
