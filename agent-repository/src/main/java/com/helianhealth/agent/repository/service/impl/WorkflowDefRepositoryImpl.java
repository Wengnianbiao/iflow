package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.repository.mapper.agent.InterfaceWorkflowDefMapper;
import com.helianhealth.agent.repository.model.assembler.WorkflowDefAssembler;
import com.helianhealth.agent.repository.model.def.WorkflowDef;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDefPO;
import com.helianhealth.agent.repository.service.WorkflowDefRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link WorkflowDefRepository} 默认实现。
 *
 * <p>版本语义对齐 conductor {@code MySQLMetadataDAO}：
 * <ul>
 *   <li>写入时若 name+version 不存在则 INSERT，存在则覆盖该版本 json_data；</li>
 *   <li>写入后将该 name 的 latest_version 统一刷新为「已存在最大版本」与「本次版本」中的较大者；</li>
 *   <li>删除某版本后，按剩余行的最大版本重置 latest_version。</li>
 * </ul>
 */
@Service
@Slf4j
public class WorkflowDefRepositoryImpl implements WorkflowDefRepository {

    private final InterfaceWorkflowDefMapper mapper;

    public WorkflowDefRepositoryImpl(InterfaceWorkflowDefMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createWorkflowDef(WorkflowDef def) {
        validate(def);
        if (mapper.countByNameAndVersion(def.getName(), def.getVersion()) > 0) {
            throw new AgentConfigException(AgentErrorCode.REPOSITORY_SAVE_ERROR,
                    "工作流定义已存在: " + def.key());
        }
        insert(def);
        refreshLatestVersion(def.getName(), def.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsertWorkflowDef(WorkflowDef def) {
        validate(def);
        if (mapper.countByNameAndVersion(def.getName(), def.getVersion()) > 0) {
            mapper.updateWorkflowDef(WorkflowDefAssembler.toPO(def));
        } else {
            insert(def);
        }
        refreshLatestVersion(def.getName(), def.getVersion());
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        return Optional.ofNullable(mapper.selectByNameAndVersion(name, version))
                .map(WorkflowDefAssembler::toDomain);
    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        return Optional.ofNullable(mapper.selectLatestByName(name))
                .map(WorkflowDefAssembler::toDomain);
    }

    @Override
    public Optional<WorkflowDef> getLatestEnabledByInterfaceUri(String interfaceUri) {
        return Optional.ofNullable(mapper.selectLatestEnabledByInterfaceUri(interfaceUri))
                .map(WorkflowDefAssembler::toDomain);
    }

    @Override
    public List<WorkflowDef> getAllVersions(String name) {
        return mapper.selectAllVersions(name).stream()
                .map(WorkflowDefAssembler::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<WorkflowDef> getAllLatest() {
        return mapper.selectAllLatest().stream()
                .map(WorkflowDefAssembler::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWorkflowDef(String name, int version) {
        int deleted = mapper.deleteByNameAndVersion(name, version);
        if (deleted == 0) {
            throw new AgentConfigException(AgentErrorCode.WORKFLOW_NOT_FOUND,
                    "工作流定义不存在: " + WorkflowDef.getKey(name, version));
        }
        Integer maxVersion = mapper.getMaxVersion(name);
        if (maxVersion != null) {
            mapper.updateLatestVersion(name, maxVersion);
        }
    }

    private void insert(WorkflowDef def) {
        InterfaceWorkflowDefPO po = WorkflowDefAssembler.toPO(def);
        po.setLatestVersion(def.getVersion());
        mapper.insertWorkflowDef(po);
    }

    /**
     * 将该 name 的 latest_version 刷新为「已存在最大版本」与「本次版本」的较大者。
     */
    private void refreshLatestVersion(String name, int currentVersion) {
        Integer maxVersion = mapper.getMaxVersion(name);
        int latest = (maxVersion == null) ? currentVersion : Math.max(maxVersion, currentVersion);
        mapper.updateLatestVersion(name, latest);
    }

    private void validate(WorkflowDef def) {
        if (def == null || def.getName() == null || def.getName().trim().isEmpty()) {
            throw new AgentConfigException(AgentErrorCode.REPOSITORY_SAVE_ERROR,
                    "工作流定义 name 不能为空");
        }
        if (def.getVersion() <= 0) {
            throw new AgentConfigException(AgentErrorCode.REPOSITORY_SAVE_ERROR,
                    "工作流定义 version 必须为正整数: " + def.getName());
        }
    }
}
