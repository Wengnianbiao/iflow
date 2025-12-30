package com.helianhealth.agent.common.enums;

import lombok.Getter;

/**
 * 映射来源枚举
 */
@Getter
public enum MappingSource {

    /**
     * 来自响应数据
     */
    RESPONSE,

    /**
     * 来自输入参数
     */
    INPUT
}
