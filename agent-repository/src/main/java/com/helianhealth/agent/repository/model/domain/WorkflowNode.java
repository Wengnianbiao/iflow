package com.helianhealth.agent.repository.model.domain;

import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流节点运行时领域模型
 * 每个工作流节点对应一次三方接口的调用
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNode {

    private Integer nodeId;

    private String nodeName;

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
     */
    private String keywordField;

    /**
     * SPI扩展点映射配置
     */
    private String remoteExtension;

    /**
     * 通用扩展参数
     * 类似 Dubbo URL 的 parameters，用于承载节点级别的可扩展配置
     */
    private Map<String, String> extensionParam;

    /**
     * 添加/更新扩展参数
     */
    public void putExtensionParam(String key, String value) {
        if (extensionParam == null) {
            extensionParam = new HashMap<>();
        }
        extensionParam.put(key, value);
    }

    /**
     * 移除扩展参数，返回是否实际移除了
     */
    public boolean removeExtensionParam(String key) {
        if (extensionParam == null || extensionParam.isEmpty()) {
            return false;
        }
        return extensionParam.remove(key) != null;
    }
}
