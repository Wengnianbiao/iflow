import request from '@/utils/request'
import axios from 'axios'

/**
 * 通过后端代理调用第三方接口
 * @param {Object} params - 调用参数
 * @param {string} params.url - 目标URL
 * @param {string} params.method - HTTP方法
 * @param {Object} params.headers - 请求头
 * @param {Object|string} params.body - 请求体
 * @param {Object} params.queryParams - URL查询参数
 */
export function proxyInvoke(params) {
  return request({
    url: '/v1/console/invoke-tool/proxy',
    method: 'post',
    data: params
  })
}

/**
 * 直接调用第三方接口（适用于无CORS限制的场景）
 * @param {Object} params - 调用参数
 */
export function directInvoke(params) {
  const { url, method, headers, body, queryParams, timeout = 30000 } = params
  
  // 创建独立的axios实例，不使用默认配置
  const instance = axios.create({
    timeout: timeout
  })
  
  const config = {
    url,
    method: method.toLowerCase(),
    headers: headers || {},
  }
  
  // 添加查询参数
  if (queryParams && Object.keys(queryParams).length > 0) {
    config.params = queryParams
  }
  
  // 添加请求体（仅对非GET方法）
  if (body && method.toUpperCase() !== 'GET') {
    config.data = body
  }
  
  return instance.request(config)
}

/**
 * 保存调用历史
 */
export function saveInvokeHistory(data) {
  return request({
    url: '/v1/console/invoke-tool/history/save',
    method: 'post',
    data
  })
}

/**
 * 获取调用历史列表
 */
export function getInvokeHistory(params) {
  return request({
    url: '/v1/console/invoke-tool/history/list',
    method: 'get',
    params
  })
}

/**
 * 删除调用历史
 */
export function deleteInvokeHistory(historyId) {
  return request({
    url: `/v1/console/invoke-tool/history/${historyId}`,
    method: 'delete'
  })
}

/**
 * 执行SQL查询
 * @param {Object} params - 查询参数
 * @param {string} params.dbType - 数据库类型 (mysql/postgresql/oracle/sqlserver/dm/kingbase)
 * @param {string} params.url - JDBC连接URL
 * @param {string} params.username - 用户名
 * @param {string} params.password - 密码
 * @param {string} params.sql - SQL语句（仅支持SELECT）
 * @param {number} params.maxRows - 最大返回行数
 */
export function executeSql(params) {
  return request({
    url: '/v1/console/invoke-tool/db/execute',
    method: 'post',
    data: params
  })
}
