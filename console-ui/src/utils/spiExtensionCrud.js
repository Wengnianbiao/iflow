import {
  deleteExtension,
  disableExtension,
  enableExtension,
  getExtensionDetail
} from '@/api/extension';

/**
 * 后端 ExtensionController 已提供统一的 detail/enable/disable/delete 接口（按 configId 操作），
 * 不再需要按 spiInterface 分发到不同 URL。以下函数保留 row 参数签名以减少调用方改动。
 */

/** @param {{ configId: number }} row */
export function loadExtensionDetailRequest(row) {
  return getExtensionDetail(row.configId);
}

/** @param {{ configId: number }} row */
export function enableExtensionRequest(row) {
  return enableExtension(row.configId);
}

/** @param {{ configId: number }} row */
export function disableExtensionRequest(row) {
  return disableExtension(row.configId);
}

/** @param {{ configId: number }} row */
export function deleteExtensionRequest(row) {
  return deleteExtension(row.configId);
}
