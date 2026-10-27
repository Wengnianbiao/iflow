package com.helianhealth.iflow.ai.model;

import java.util.Objects;

/**
 * MCP 健康检查视图。
 */
public class HealthCheckMcpView {

    /**
     * 整体状态：UP / DOWN
     */
    private String status;

    /**
     * MCP 服务名称
     */
    private String serverName;

    /**
     * MCP 服务版本
     */
    private String serverVersion;

    /**
     * 数据库连通性：UP / DOWN / SKIPPED
     */
    private String database;

    /**
     * 补充说明（失败原因等）
     */
    private String message;

    public String getStatus() {
        return this.status;
    }

    public String getServerName() {
        return this.serverName;
    }

    public String getServerVersion() {
        return this.serverVersion;
    }

    public String getDatabase() {
        return this.database;
    }

    public String getMessage() {
        return this.message;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    protected boolean canEqual(Object other) {
        return other instanceof HealthCheckMcpView;
    }

    public HealthCheckMcpView() {
    }

    public HealthCheckMcpView(
            String status,
            String serverName,
            String serverVersion,
            String database,
            String message) {
        this.status = status;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.database = database;
        this.message = message;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof HealthCheckMcpView other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getStatus(), other.getStatus())
                && Objects.equals(getServerName(), other.getServerName())
                && Objects.equals(getServerVersion(), other.getServerVersion())
                && Objects.equals(getDatabase(), other.getDatabase())
                && Objects.equals(getMessage(), other.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getStatus(),
                getServerName(),
                getServerVersion(),
                getDatabase(),
                getMessage());
    }

    @Override
    public String toString() {
        return "HealthCheckMcpView(status=" + getStatus()
                + ", serverName=" + getServerName()
                + ", serverVersion=" + getServerVersion()
                + ", database=" + getDatabase()
                + ", message=" + getMessage() + ")";
    }

    public static class HealthCheckMcpViewBuilder {

        private String status;
        private String serverName;
        private String serverVersion;
        private String database;
        private String message;

        HealthCheckMcpViewBuilder() {
        }

        public HealthCheckMcpViewBuilder status(String status) {
            this.status = status;
            return this;
        }

        public HealthCheckMcpViewBuilder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public HealthCheckMcpViewBuilder serverVersion(String serverVersion) {
            this.serverVersion = serverVersion;
            return this;
        }

        public HealthCheckMcpViewBuilder database(String database) {
            this.database = database;
            return this;
        }

        public HealthCheckMcpViewBuilder message(String message) {
            this.message = message;
            return this;
        }

        public HealthCheckMcpView build() {
            return new HealthCheckMcpView(
                    status,
                    serverName,
                    serverVersion,
                    database,
                    message);
        }

        @Override
        public String toString() {
            return "HealthCheckMcpView.HealthCheckMcpViewBuilder(status=" + status
                    + ", serverName=" + serverName
                    + ", serverVersion=" + serverVersion
                    + ", database=" + database
                    + ", message=" + message + ")";
        }
    }

    public static HealthCheckMcpViewBuilder builder() {
        return new HealthCheckMcpViewBuilder();
    }
}
