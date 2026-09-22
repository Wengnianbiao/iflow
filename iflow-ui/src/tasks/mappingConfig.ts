export const PARAM_TYPES = ['STRING', 'INTEGER', 'LONG', 'DOUBLE', 'BOOLEAN', 'OBJECT', 'ARRAY'] as const
export const MAPPING_TYPES = ['NAME', 'CONSTANT', 'EXPRESSION', 'OBJECT', 'ARRAY_MAP', 'ARRAY_FIXED'] as const
export type ParamType = (typeof PARAM_TYPES)[number]
export type MappingType = (typeof MAPPING_TYPES)[number]
export type ConfigPath = number[]

export function mappingTypesForTargetType(targetType: ParamType): MappingType[] {
  if (targetType === 'ARRAY') return ['ARRAY_MAP', 'ARRAY_FIXED', 'EXPRESSION']
  if (targetType === 'OBJECT') return ['OBJECT', 'EXPRESSION']
  return ['NAME', 'CONSTANT', 'EXPRESSION']
}

export type DisplayConfigRow = {
  config: ParamMappingConfig
  path: ConfigPath
  depth: number
  ancestors: ConfigPath[]
}
export interface ParamMappingConfig {
  targetParamKey: string
  targetParamType: ParamType
  mappingType: MappingType
  sourceParamKey?: string
  mappingRule?: unknown
  paramDesc?: string
  childParams: ParamMappingConfig[]
  itemAlias?: string
  itemTemplate?: ParamMappingConfig
  items: ParamMappingConfig[]
}

export function loopElementLabel(config?: Pick<ParamMappingConfig, 'targetParamType'>): string {
  if (!config) return '元素'
  if (config.targetParamType === 'OBJECT') return '对象元素'
  if (config.targetParamType === 'ARRAY') return '数组元素'
  return '标量元素'
}

export function configChildren(config: ParamMappingConfig): ParamMappingConfig[] {
  if (config.mappingType === 'OBJECT') return config.childParams
  if (config.mappingType === 'ARRAY_FIXED') return config.items
  if (config.mappingType === 'ARRAY_MAP') return config.itemTemplate ? [config.itemTemplate] : []
  return []
}

export function withConfigChildren(config: ParamMappingConfig, children: ParamMappingConfig[]): ParamMappingConfig {
  if (config.mappingType === 'OBJECT') return { ...config, childParams: children }
  if (config.mappingType === 'ARRAY_FIXED') return { ...config, items: children }
  if (config.mappingType === 'ARRAY_MAP') return { ...config, itemTemplate: children[0] }
  return config
}

export function normalizeConfig(value: unknown): ParamMappingConfig {
  const raw = value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
  return {
    targetParamKey: typeof raw.targetParamKey === 'string' ? raw.targetParamKey : '',
    targetParamType: PARAM_TYPES.includes(raw.targetParamType as ParamType) ? raw.targetParamType as ParamType : 'STRING',
    mappingType: MAPPING_TYPES.includes(raw.mappingType as MappingType) ? raw.mappingType as MappingType : 'NAME',
    sourceParamKey: typeof raw.sourceParamKey === 'string' ? raw.sourceParamKey : '',
    mappingRule: raw.mappingRule,
    paramDesc: typeof raw.paramDesc === 'string' ? raw.paramDesc : '',
    childParams: Array.isArray(raw.childParams) ? raw.childParams.map(normalizeConfig) : [],
    itemAlias: typeof raw.itemAlias === 'string' ? raw.itemAlias : undefined,
    itemTemplate: raw.itemTemplate == null ? undefined : normalizeConfig(raw.itemTemplate),
    items: Array.isArray(raw.items) ? raw.items.map(normalizeConfig) : [],
  }
}

export function emptyConfig(sourceRoot = '$'): ParamMappingConfig {
  return normalizeConfig({ sourceParamKey: sourceRoot, targetParamType: 'STRING', mappingType: 'NAME' })
}

export function objectTemplate(): ParamMappingConfig {
  return normalizeConfig({ targetParamType: 'OBJECT', mappingType: 'OBJECT' })
}

export function normalizeForSave(value: ParamMappingConfig): ParamMappingConfig {
  const mode = value.mappingType
  return {
    targetParamKey: value.targetParamKey.trim(),
    targetParamType: mode === 'OBJECT' ? 'OBJECT' : mode === 'ARRAY_MAP' || mode === 'ARRAY_FIXED' ? 'ARRAY' : value.targetParamType,
    mappingType: mode,
    paramDesc: value.paramDesc?.trim(),
    sourceParamKey: mode === 'NAME' || mode === 'ARRAY_MAP' ? value.sourceParamKey?.trim() : undefined,
    mappingRule: mode === 'CONSTANT' ? value.mappingRule ?? null
      : mode === 'EXPRESSION' && typeof value.mappingRule === 'string' ? value.mappingRule.trim() : undefined,
    childParams: mode === 'OBJECT' ? value.childParams.map(normalizeForSave) : [],
    itemAlias: mode === 'ARRAY_MAP' ? value.itemAlias?.trim() : undefined,
    itemTemplate: mode === 'ARRAY_MAP' && value.itemTemplate ? normalizeForSave(value.itemTemplate) : undefined,
    items: mode === 'ARRAY_FIXED' ? value.items.map(normalizeForSave) : [],
  }
}

export function configAtPath(configs: ParamMappingConfig[], path: ConfigPath): ParamMappingConfig | undefined {
  let children = configs
  let config: ParamMappingConfig | undefined
  for (const index of path) {
    config = children[index]
    if (!config) return undefined
    children = configChildren(config)
  }
  return config
}

export function updateAtPath(configs: ParamMappingConfig[], path: ConfigPath,
  update: (config: ParamMappingConfig) => ParamMappingConfig): ParamMappingConfig[] {
  const [index, ...rest] = path
  return configs.map((config, i) => i !== index ? config : rest.length
    ? withConfigChildren(config, updateAtPath(configChildren(config), rest, update)) : update(config))
}

export function removeAtPath(configs: ParamMappingConfig[], path: ConfigPath): ParamMappingConfig[] {
  const [index, ...rest] = path
  if (!rest.length) return configs.filter((_, i) => i !== index)
  return configs.map((config, i) => i !== index || config.mappingType === 'ARRAY_MAP' && rest.length === 1
    ? config : withConfigChildren(config, removeAtPath(configChildren(config), rest)))
}

export function insertAfterPath(configs: ParamMappingConfig[], path: ConfigPath, value: ParamMappingConfig): ParamMappingConfig[] {
  const [index, ...rest] = path
  if (!rest.length) return [...configs.slice(0, index + 1), value, ...configs.slice(index + 1)]
  return configs.map((config, i) => i !== index || config.mappingType === 'ARRAY_MAP' && rest.length === 1
    ? config : withConfigChildren(config, insertAfterPath(configChildren(config), rest, value)))
}

export function flattenConfigs(configs: ParamMappingConfig[], parent: ConfigPath = []):
Array<{ config: ParamMappingConfig; path: ConfigPath; depth: number }> {
  return configs.flatMap((config, index) => {
    const path = [...parent, index]
    return [{ config, path, depth: path.length - 1 }, ...flattenConfigs(configChildren(config), path)]
  })
}

/** Builds the editor tree with ARRAY_MAP's single element template shown explicitly. */
export function flattenDisplayConfigs(configs: ParamMappingConfig[], parent: ConfigPath = [],
  depth = 0, ancestors: ConfigPath[] = []): DisplayConfigRow[] {
  return configs.flatMap((config, index) => {
    const path = [...parent, index]
    const row: DisplayConfigRow = { config, path, depth, ancestors }
    const visibleAncestors = [...ancestors, path]
    return [row, ...flattenDisplayConfigs(configChildren(config), path, depth + 1, visibleAncestors)]
  })
}

export function scopeAliases(configs: ParamMappingConfig[], parentPath: ConfigPath): string[] {
  return parentPath.flatMap((_, i) => {
    const node = configAtPath(configs, parentPath.slice(0, i + 1))
    return node?.mappingType === 'ARRAY_MAP' && node.itemAlias ? [node.itemAlias] : []
  })
}

export function configNodeLabel(configs: ParamMappingConfig[], path: ConfigPath): string {
  const node = configAtPath(configs, path)
  const parent = configAtPath(configs, path.slice(0, -1))
  if (parent?.mappingType === 'ARRAY_MAP') return loopElementLabel(node)
  if (parent?.mappingType === 'ARRAY_FIXED') return `[${path[path.length - 1]}]`
  return node?.targetParamKey || '未命名字段'
}

const PATH_IDENTIFIER = /^[A-Za-z_$][A-Za-z0-9_$]*$/

export function sourcePathForKey(root: string, key: string): string {
  if (!key) return root
  if (PATH_IDENTIFIER.test(key)) return `${root}.${key}`
  return `${root}['${key.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}']`
}

export function sourcePathAfterTargetRename(currentPath: string | undefined, root: string,
  previousKey: string, nextKey: string): string | undefined {
  return currentPath === sourcePathForKey(root, previousKey) ? sourcePathForKey(root, nextKey) : currentPath
}

export function aliasPathCorrection(path: string, aliases: string[]): string | undefined {
  for (const alias of aliases) {
    for (const prefix of [`$.${alias}`, `$['${alias}']`, `$["${alias}"]`]) {
      if (path === prefix) return alias
      if (path.startsWith(`${prefix}.`) || path.startsWith(`${prefix}[`)) return `${alias}${path.slice(prefix.length)}`
    }
  }
  return undefined
}

export function countMappingTargetFields(value: unknown): number {
  if (!Array.isArray(value)) return 0
  return value.reduce((count, raw) => {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return count
    const config = raw as Record<string, unknown>
    return count + (typeof config.targetParamKey === 'string' && config.targetParamKey ? 1 : 0)
      + countMappingTargetFields(config.childParams) + countMappingTargetFields(config.items)
      + countMappingTargetFields(config.itemTemplate ? [config.itemTemplate] : [])
  }, 0)
}

const RESERVED = new Set('$ XML Crypto arguments eval undefined NaN Infinity globalThis Object Function Boolean Symbol Number BigInt Math Date String RegExp Array JSON Map Set WeakMap WeakSet Promise Reflect Proxy Intl Atomics WebAssembly console Error TypeError RangeError SyntaxError ReferenceError URIError ArrayBuffer SharedArrayBuffer DataView Int8Array Uint8Array Uint8ClampedArray Int16Array Uint16Array Int32Array Uint32Array Float32Array Float64Array BigInt64Array BigUint64Array parseInt parseFloat isNaN isFinite encodeURI decodeURI encodeURIComponent decodeURIComponent escape unescape break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof new return super switch this throw try typeof var void while with yield let static enum await implements package protected interface private public null true false'.split(' '))

export function validateNode(config: ParamMappingConfig, aliases: string[], element: boolean): void {
  if (!element && !config.targetParamKey.trim()) throw new Error('目标参数名不能为空')
  if (element && config.targetParamKey) throw new Error('数组元素模板不填写目标参数名')
  if (config.targetParamType === 'ARRAY' && !mappingTypesForTargetType('ARRAY').includes(config.mappingType)) {
    throw new Error('数组仅支持循环映射、固定长度或 JS 表达式')
  }
  if (config.targetParamType === 'OBJECT' && !mappingTypesForTargetType('OBJECT').includes(config.mappingType)) {
    throw new Error('对象仅支持对象构建或 JS 表达式')
  }
  if (config.mappingType === 'NAME' || config.mappingType === 'ARRAY_MAP') {
    const path = config.sourceParamKey ?? ''
    const root = path.match(/^([A-Za-z_$][A-Za-z0-9_$]*)(?=\.|\[|$)/)?.[1]
    if (!root || root !== '$' && !aliases.includes(root)) throw new Error('取值路径必须以 $ 或当前可用的循环变量开头')
    if (!/^(?:\.[^.\[\]\s*?]+|\[\d+\]|\['(?:[^'\\]|\\.)*'\]|\["(?:[^"\\]|\\.)*"\])*$/.test(path.slice(root.length))) {
      throw new Error('取值路径仅支持字段和数组下标，请检查路径语法')
    }
  }
  if (config.mappingType === 'EXPRESSION' && (typeof config.mappingRule !== 'string' || !config.mappingRule.trim())) {
    throw new Error('JS 表达式不能为空')
  }
  if (config.mappingType === 'ARRAY_MAP') {
    const alias = config.itemAlias ?? ''
    if (!/^[A-Za-z_$][A-Za-z0-9_$]*$/.test(alias) || RESERVED.has(alias)) throw new Error('循环变量名无效或使用了保留名称')
    if (aliases.includes(alias)) throw new Error('循环变量不能与外层变量重名')
    if (!config.itemTemplate) throw new Error('循环数组必须配置一个元素模板')
  }
}

export function parseMappingConfigs(raw: unknown): ParamMappingConfig[] {
  const read = (values: unknown, aliases: string[], element: boolean, path: string): ParamMappingConfig[] => {
    if (!Array.isArray(values)) throw new Error(`${path} 必须是数组`)
    const keys = new Set<string>()
    return values.map((value, i) => {
      const location = `${path}[${i}]`
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${location} 必须是配置对象`)
      const node = value as Record<string, unknown>
      if ('sourceParamType' in node) throw new Error(`${location}：sourceParamType 已移除，请使用新映射模型`)
      const allowed = ['targetParamKey', 'targetParamType', 'mappingType', 'sourceParamKey', 'mappingRule', 'paramDesc', 'childParams', 'itemAlias', 'itemTemplate', 'items']
      const unknown = Object.keys(node).find(key => !allowed.includes(key))
      if (unknown) throw new Error(`${location}：未知配置字段 ${unknown}`)
      for (const key of ['childParams', 'items']) {
        if (node[key] != null && !Array.isArray(node[key])) throw new Error(`${location}.${key} 必须是数组`)
      }
      if (!MAPPING_TYPES.includes(node.mappingType as MappingType) || !PARAM_TYPES.includes(node.targetParamType as ParamType)) {
        throw new Error(`${location}：必须指定有效的 mappingType 和 targetParamType`)
      }
      const config = normalizeConfig(node)
      try { validateNode(config, aliases, element) } catch (error) { throw new Error(`${location}：${(error as Error).message}`) }
      if (!element && keys.has(config.targetParamKey)) throw new Error(`${location}：目标字段重复`)
      keys.add(config.targetParamKey)
      const mode = config.mappingType
      if (mode === 'OBJECT' && config.targetParamType !== 'OBJECT'
        || (mode === 'ARRAY_MAP' || mode === 'ARRAY_FIXED') && config.targetParamType !== 'ARRAY') throw new Error(`${location}：结构节点目标类型不匹配`)
      if (mode !== 'OBJECT' && config.childParams.length || mode !== 'ARRAY_FIXED' && config.items.length
        || mode !== 'ARRAY_MAP' && (config.itemTemplate || config.itemAlias != null)) throw new Error(`${location}：节点包含不适用的结构配置`)
      if (mode === 'OBJECT') config.childParams = read(node.childParams ?? [], aliases, false, `${location}.childParams`)
      if (mode === 'ARRAY_FIXED') config.items = read(node.items ?? [], aliases, true, `${location}.items`)
      if (mode === 'ARRAY_MAP') config.itemTemplate = read([node.itemTemplate], [...aliases, config.itemAlias!], true, `${location}.itemTemplate`)[0]
      return config
    })
  }
  return read(raw, [], false, 'paramsConfig')
}
