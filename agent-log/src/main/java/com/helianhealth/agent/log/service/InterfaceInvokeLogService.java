package com.helianhealth.agent.log.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.log.model.InterfaceInvokeLogDO;

public interface InterfaceInvokeLogService {

    /**
     * 保存接口调用日志
     *
     * @param nodeName             节点名称
     * @param keyword              关键字
     * @param businessData         业务数据
     * @param paramBeforeInvoke    调用前参数
     * @param remoteInvokeResponse 远程调用响应
     * @param paramAfterInvoke     调用后参数
     */
    void saveInvokeLog(String nodeName,
                       String keyword,
                       String businessData,
                       String paramBeforeInvoke,
                       String remoteInvokeResponse,
                       String paramAfterInvoke,
                       String invokeTime);

    /**
     * 自定义调用逻辑可用，仅保存调用参数和响应结果
     *
     * @param nodeName             节点名称
     * @param keyword              关键字
     * @param paramBeforeInvoke    调用前参数
     * @param remoteInvokeResponse 响应结果
     * @param invokeTime           调用时间
     */
    void saveInvokeLog(String nodeName,
                       String keyword,
                       String paramBeforeInvoke,
                       String remoteInvokeResponse,
                       String invokeTime);

    PageList<InterfaceInvokeLogDO> findAllInvokeLogs(String nodeName, String keyword, int pageNum, int pageSize);
}
