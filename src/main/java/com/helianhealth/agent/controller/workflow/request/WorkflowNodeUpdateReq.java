package com.helianhealth.agent.controller.workflow.request;

import com.helianhealth.agent.enums.NodeType;
import com.helianhealth.agent.enums.ScheduleParamSourceType;
import com.helianhealth.agent.utils.UrlWrapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 工作流节点更新请求
 */
@Data
public class WorkflowNodeUpdateReq {

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
    private String nodeType;

    /**
     * api实例元数据,以key-value封装的Json数据
     * e.g. 模式为Http下则包含请求的url、请求头、请求方式等
     * e.g. 模式为Database下则包含数据库连接信息、数据库表名、数据库字段名等
     */
    private String metaInfo;

    /**
     * 参数拆分规则表达式
     * 当前业务场景只根据列表拆分为单个对象或基础数据类型
     * e.g. ids:["12","32"] -> {"id":"12","id":"32"}
     */
    private String splitRule;

    /**
     * 调度规则表达式
     */
    private String scheduleExpr;

    /**
     * 调度参数来源类型
     */
    private String scheduleParamSourceType;

    /**
     * 业务关键字字段名配置
     * 用于指定从businessData中提取哪个字段作为关键字
     */
    private String keywordField;

    /**
     * SPI拓展字段
     */
    private String remoteExtension;
}
