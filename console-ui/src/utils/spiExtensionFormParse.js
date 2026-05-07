/**
 * @param {object} detailRow
 * @returns {number[]}
 */
export function parseBindingIds(detailRow) {
  let bindingIds = detailRow.bindingIds;
  if (bindingIds && !Array.isArray(bindingIds)) {
    try {
      bindingIds = typeof bindingIds === 'string' ? JSON.parse(bindingIds) : [bindingIds];
    } catch (e) {
      bindingIds = [];
    }
  } else if (!bindingIds) {
    bindingIds = [];
  }
  return bindingIds;
}

/**
 * @param {object} detailRow
 * @returns {string}
 */
export function resolveExtensionMode(detailRow) {
  return (
    detailRow.extensionMode ||
    detailRow.extensionLocation ||
    (detailRow.usePageCode === 1 ? 'ONLINE' : 'PROJECT') ||
    'ONLINE'
  );
}
