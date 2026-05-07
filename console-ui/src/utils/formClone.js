/** 表单纯 JSON 可序列化字段的深拷贝（用于弹窗本地编辑副本） */
export function cloneFormData(obj) {
  if (obj == null) return {};
  try {
    return JSON.parse(JSON.stringify(obj));
  } catch (e) {
    return { ...obj };
  }
}
