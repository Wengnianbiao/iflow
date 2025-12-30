package com.helianhealth.agent.invocation.remote.model;

import com.helianhealth.agent.common.enums.ParamType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 参数树节点
 * 用于组装树形结构，参数的本质是一个多叉树（N-ary tree）
 */
@Data
@Builder
public class ParamTreeNode {
    
    /**
     * 参数键名
     */
    private String paramKey;

    /**
     * 参数值（基本类型 / 子节点列表）
     * 根据 paramType 进行类型转换，确保类型安全
     */
    private Object paramValue;

    /**
     * 节点类型（OBJECT、ARRAY、STRING、INTEGER 等）
     */
    private ParamType paramType;

    /**
     * 是否保留单个元素 key（当且仅当对象兼容场景）
     */
    private Boolean keepSingleElementKey;

    /**
     * XML 的属性节点
     */
    private List<ParamTreeNode> attributeNodes;

    /**
     * 子节点（用于 OBJECT / ARRAY 类型）
     */
    private List<ParamTreeNode> children;
}
