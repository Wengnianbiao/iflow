package com.helianhealth.agent.repository.model.assembler;

import com.helianhealth.agent.repository.model.domain.InvokeLog;
import com.helianhealth.agent.repository.model.po.InterfaceInvokeLogPO;

/**
 * 调用日志 PO/Domain 转换器
 */
public final class InvokeLogAssembler {

    private InvokeLogAssembler() {
    }

    public static InvokeLog toDomain(InterfaceInvokeLogPO po) {
        if (po == null) {
            return null;
        }
        return InvokeLog.builder()
                .logId(po.getLogId())
                .nodeName(po.getNodeName())
                .keyword(po.getKeyword())
                .businessData(po.getBusinessData())
                .paramBeforeInvoke(po.getParamBeforeInvoke())
                .remoteInvokeResponse(po.getRemoteInvokeResponse())
                .remoteInvokeResponseParsed(po.getRemoteInvokeResponseParsed())
                .paramAfterInvoke(po.getParamAfterInvoke())
                .invokeTime(po.getInvokeTime())
                .errorMessage(po.getErrorMessage())
                .createTime(po.getCreateTime())
                .build();
    }

    public static InterfaceInvokeLogPO toPO(InvokeLog log) {
        if (log == null) {
            return null;
        }
        return InterfaceInvokeLogPO.builder()
                .logId(log.getLogId())
                .nodeName(log.getNodeName())
                .keyword(log.getKeyword())
                .businessData(log.getBusinessData())
                .paramBeforeInvoke(log.getParamBeforeInvoke())
                .remoteInvokeResponse(log.getRemoteInvokeResponse())
                .remoteInvokeResponseParsed(log.getRemoteInvokeResponseParsed())
                .paramAfterInvoke(log.getParamAfterInvoke())
                .invokeTime(log.getInvokeTime())
                .errorMessage(log.getErrorMessage())
                .createTime(log.getCreateTime())
                .build();
    }
}
