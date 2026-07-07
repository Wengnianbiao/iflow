package com.helianhealth.agent.common.enums;

import lombok.Getter;

/**
 * 处理类型枚举
 */
@Getter
public enum ProcessType {

    /**
     * 前置处理（请求参数映射）
     */
    PRE_PROCESS,

    /**
     * 后置处理（响应参数映射）
     */
    POST_PROCESS
}
