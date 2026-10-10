import type { WorkflowDef } from '@/types/workflow'

export function normalizeWorkflowTags(tags?: readonly string[] | null): string[] {
  return [...new Set((tags ?? []).map((tag) => tag.trim()).filter(Boolean))]
}

export function collectWorkflowTags(definitions: WorkflowDef[]): string[] {
  return [...new Set(definitions.flatMap((definition) => normalizeWorkflowTags(definition.tags)))]
    .sort((left, right) => left.localeCompare(right, 'zh-CN'))
}

export function filterWorkflowDefinitions(
  definitions: WorkflowDef[],
  search: string,
  selectedTags: string[],
): WorkflowDef[] {
  const keyword = search.trim().toLocaleLowerCase()
  return definitions.filter((definition) => {
    const tags = normalizeWorkflowTags(definition.tags)
    return (!selectedTags.length || selectedTags.some((tag) => tags.includes(tag)))
      && (!keyword || [definition.route, definition.name, definition.description, ...tags]
        .some((value) => value?.toLocaleLowerCase().includes(keyword)))
  })
}
