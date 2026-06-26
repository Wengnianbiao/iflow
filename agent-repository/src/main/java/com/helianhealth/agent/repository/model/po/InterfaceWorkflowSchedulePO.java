package com.helianhealth.agent.repository.model.po;

import com.helianhealth.agent.common.typehandler.ListIntegerTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.JdbcType;
import tk.mybatis.mapper.annotation.ColumnType;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.List;

/**
 * 工作流调度（聚合根）PO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interface_workflow_schedule")
public class InterfaceWorkflowSchedulePO {

    @Id
    @Column(name = "schedule_id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer scheduleId;

    @Column(name = "flow_id")
    private Integer flowId;

    @Column(name = "schedule_name")
    private String scheduleName;

    @Column(name = "entry_node_ids")
    @ColumnType(typeHandler = ListIntegerTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private List<Integer> entryNodeIds;

    @Column(name = "status")
    private Integer status;
}
