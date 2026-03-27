-- =====================================================
-- interface-agent 升级脚本 v1.2.0 (SQL Server)
-- =====================================================

-- interface_invoke_log 新增 remote_invoke_response_parsed 字段
ALTER TABLE interface_invoke_log ADD remote_invoke_response_parsed nvarchar(max);
