package com.helianhealth.agent.repository.model.po;

import com.helianhealth.agent.common.enums.WorkflowContentType;
import com.helianhealth.agent.common.typehandler.ListIntegerTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;
import tk.mybatis.mapper.annotation.ColumnType;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.List;

/**
 * 接口工作流
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interface_workflow")
public class InterfaceWorkflowPO {

    @Id
    @Column(name = "flow_id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer flowId;

    @Column(name = "flow_name")
    private String flowName;

    @Column(name = "interface_uri")
    private String interfaceUri;

    @Column(name = "content_type")
    @Enumerated(EnumType.STRING)
    private WorkflowContentType contentType;

    @Column(name = "content_meta_info")
    private String contentMetaInfo;

    @Column(name = "first_flow_nodes")
    @ColumnType(typeHandler = ListIntegerTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private List<Integer> firstFlowNodes;

    @Column(name = "status")
    private Integer status;
}
