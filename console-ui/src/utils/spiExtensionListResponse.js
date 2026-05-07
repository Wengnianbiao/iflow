/**
 * 将接口返回规范化为扩展行数组
 */
export function normalizeExtensionListResponse(resp) {
  if (!resp || !resp.data) return [];
  const result = resp.data;
  return Array.isArray(result.data) ? result.data : (Array.isArray(result) ? result : []);
}
