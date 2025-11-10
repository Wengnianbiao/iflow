package com.helianhealth.agent.service;

import com.helianhealth.agent.common.PageList;
import com.helianhealth.agent.model.domain.InterfaceInvokeLogDO;

public interface InterfaceInvokeLogService {

    /**
     * 保存接口调用日志
     * @param nodeName 节点名称
     * @param keyword 关键字
     * @param businessData 业务数据
     * @param paramBeforeInvoke 调用前参数
     * @param remoteInvokeResponse 远程调用响应
     * @param paramAfterInvoke 调用后参数
     */
    void saveInvokeLog(String nodeName,
                       String keyword,
                       String businessData,
                       String paramBeforeInvoke,
                       String remoteInvokeResponse,
                       String paramAfterInvoke,
                       String invokeTime);

    PageList<InterfaceInvokeLogDO> findAllInvokeLogs(String nodeName, String keyword, int pageNum, int pageSize);
}
