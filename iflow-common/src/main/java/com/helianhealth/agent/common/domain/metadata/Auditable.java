package com.helianhealth.agent.common.domain.metadata;

/**
 * 元数据审计信息。
 *
 * <p>记录元数据的创建、更新时间及操作人。时间未设置时统一返回 {@code 0}。
 */
public abstract class Auditable {

    private Long createTime;

    private Long updateTime;

    private String createdBy;

    private String updatedBy;

    public Long getCreateTime() {
        return createTime == null ? 0 : createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime == null ? 0 : updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
