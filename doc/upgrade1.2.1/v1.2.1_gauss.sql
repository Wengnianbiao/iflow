-- =====================================================
-- interface-agent 升级脚本 v1.2.0 (GaussDB)
-- =====================================================

-- interface_invoke_log 新增 remote_invoke_response_parsed 字段
ALTER TABLE interface_invoke_log ADD COLUMN remote_invoke_response_parsed TEXT;
