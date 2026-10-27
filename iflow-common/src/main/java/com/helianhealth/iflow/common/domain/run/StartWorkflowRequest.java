package com.helianhealth.iflow.common.domain.run;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 启动工作流请求。
 *
 * <p>API / Console / Gateway 入参：通过 {@code route + version} 定位定义（{@code route} 即 {@link
 * WorkflowDef#getRoute()}；{@code version}
 * 为 {@code null} 时取最新版本），{@code input} 写入运行时工作流实例。定义解析由
 * {@code WorkflowExecutionService} 完成，调用方无需自行查询 {@code WorkflowDef}。
 *
 * <p>业务关联 ID（{@code correlationId}）不由调用方传入，启动时由定义上的 {@code correlationExpression}
 * 表达式解析生成。
 */
public class StartWorkflowRequest {

    private String route;

    /**
     * 版本；为 {@code null} 时取最新版本
     * */
    private Integer version;

    private Map<String, Object> input = new HashMap<>();

    public String getRoute() {
        return this.route;
    }

    public Integer getVersion() {
        return this.version;
    }

    public Map<String, Object> getInput() {
        return this.input;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    protected boolean canEqual(Object other) {
        return other instanceof StartWorkflowRequest;
    }

    public StartWorkflowRequest() {
    }

    public StartWorkflowRequest(String route, Integer version, Map<String, Object> input) {
        this.route = route;
        this.version = version;
        this.input = input;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StartWorkflowRequest other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getRoute(), other.getRoute())
                && Objects.equals(getVersion(), other.getVersion())
                && Objects.equals(getInput(), other.getInput());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getRoute(),
                getVersion(),
                getInput());
    }

    @Override
    public String toString() {
        return "StartWorkflowRequest(route=" + getRoute()
                + ", version=" + getVersion()
                + ", input=" + getInput() + ")";
    }

    public static class StartWorkflowRequestBuilder {

        private String route;
        private Integer version;
        private Map<String, Object> input;
        private boolean inputSet;

        StartWorkflowRequestBuilder() {
        }

        public StartWorkflowRequestBuilder route(String route) {
            this.route = route;
            return this;
        }

        public StartWorkflowRequestBuilder version(Integer version) {
            this.version = version;
            return this;
        }

        public StartWorkflowRequestBuilder input(Map<String, Object> input) {
            this.input = input;
            this.inputSet = true;
            return this;
        }

        public StartWorkflowRequest build() {
            return new StartWorkflowRequest(
                    route,
                    version,
                    inputSet ? input : new HashMap<>());
        }

        @Override
        public String toString() {
            return "StartWorkflowRequest.StartWorkflowRequestBuilder(route=" + route
                    + ", version=" + version
                    + ", input=" + input + ")";
        }
    }

    public static StartWorkflowRequestBuilder builder() {
        return new StartWorkflowRequestBuilder();
    }
}
