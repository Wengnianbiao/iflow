package com.helianhealth.iflow.console.model.response;

import java.util.Objects;

public class WorkflowDefinitionSummary {

    private String route;

    private int version;

    private String name;

    private String description;

    private Long createTime;

    private Long updateTime;

    WorkflowDefinitionSummary(
            String route,
            int version,
            String name,
            String description,
            Long createTime,
            Long updateTime) {
        this.route = route;
        this.version = version;
        this.name = name;
        this.description = description;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public String getRoute() {
        return this.route;
    }

    public int getVersion() {
        return this.version;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public Long getCreateTime() {
        return this.createTime;
    }

    public Long getUpdateTime() {
        return this.updateTime;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    protected boolean canEqual(Object other) {
        return other instanceof WorkflowDefinitionSummary;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WorkflowDefinitionSummary other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getRoute(), other.getRoute())
                && getVersion() == other.getVersion()
                && Objects.equals(getName(), other.getName())
                && Objects.equals(getDescription(), other.getDescription())
                && Objects.equals(getCreateTime(), other.getCreateTime())
                && Objects.equals(getUpdateTime(), other.getUpdateTime());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getRoute(),
                getVersion(),
                getName(),
                getDescription(),
                getCreateTime(),
                getUpdateTime());
    }

    @Override
    public String toString() {
        return "WorkflowDefinitionSummary(route=" + getRoute()
                + ", version=" + getVersion()
                + ", name=" + getName()
                + ", description=" + getDescription()
                + ", createTime=" + getCreateTime()
                + ", updateTime=" + getUpdateTime() + ")";
    }

    public static class WorkflowDefinitionSummaryBuilder {

        private String route;
        private int version;
        private String name;
        private String description;
        private Long createTime;
        private Long updateTime;

        WorkflowDefinitionSummaryBuilder() {
        }

        public WorkflowDefinitionSummaryBuilder route(String route) {
            this.route = route;
            return this;
        }

        public WorkflowDefinitionSummaryBuilder version(int version) {
            this.version = version;
            return this;
        }

        public WorkflowDefinitionSummaryBuilder name(String name) {
            this.name = name;
            return this;
        }

        public WorkflowDefinitionSummaryBuilder description(String description) {
            this.description = description;
            return this;
        }

        public WorkflowDefinitionSummaryBuilder createTime(Long createTime) {
            this.createTime = createTime;
            return this;
        }

        public WorkflowDefinitionSummaryBuilder updateTime(Long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public WorkflowDefinitionSummary build() {
            return new WorkflowDefinitionSummary(
                    route,
                    version,
                    name,
                    description,
                    createTime,
                    updateTime);
        }

        @Override
        public String toString() {
            return "WorkflowDefinitionSummary.WorkflowDefinitionSummaryBuilder(route=" + route
                    + ", version=" + version
                    + ", name=" + name
                    + ", description=" + description
                    + ", createTime=" + createTime
                    + ", updateTime=" + updateTime + ")";
        }
    }

    public static WorkflowDefinitionSummaryBuilder builder() {
        return new WorkflowDefinitionSummaryBuilder();
    }
}
