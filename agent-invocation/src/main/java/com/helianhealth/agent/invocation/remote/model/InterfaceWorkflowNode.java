package com.helianhealth.agent.invocation.remote.model;

import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 接口规则引擎工作流节点
 * 每个工作流节点对应的是三方接口的调用
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Builder
public class InterfaceWorkflowNode {

    private Integer nodeId;

    /**
     * 节点描述
     */
    private String nodeName;

    /**
     * 工作流id
     */
    private Integer flowId;

    /**
     * 参数过滤表达式
     */
    private String paramFilterExpr;

    /**
     * 节点厂商类型
     */
    private NodeType nodeType;

    /**
     * api实例元数据,以key-value封装的Json数据
     * e.g. 模式为Http下则包含请求的url、请求头、请求方式等
     * e.g. 模式为Database下则包含数据库连接信息、数据库表名、数据库字段名等
     */
    private String metaInfo;

    /**
     * 参数拆分字段
     * 当前业务场景只根据列表拆分为单个对象或基础数据类型
     * e.g. ids:["12","32"] -> {"id":"12","id":"32"}
     */
    private String splitField;

    /**
     * 调度规则表达式
     */
    private String scheduleExpr;

    /**
     * 调度参数来源类型
     */
    private ScheduleParamSourceType scheduleParamSourceType;

    /**
     * 业务关键字字段名配置
     * 用于指定从businessData中提取哪个字段作为关键字
     */
    private String keywordField;

    /**
     * SPI扩展点映射配置
     */
    private String remoteExtension;

    /**
     * 通用扩展参数
     * 类似 Dubbo URL 的 parameters，用于承载节点级别的可扩展配置
     * 建议使用有前缀的 key 做命名空间，例如：
     * filter.mock=123, invoker.signatureType=HMAC
     */
    private Map<String, String> extensionParam;
}
