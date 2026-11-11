package com.helianhealth.iflow.common.domain.metadata.workflow;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;

import java.time.Instant;
import java.util.regex.Pattern;

public class WorkflowSchedule {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,255}$");

    private String name;
    private String displayName;
    private String cronExpression;
    private String zoneId;
    private Boolean enabled;
    private StartWorkflowRequest startWorkflowRequest;
    private Instant nextRunAt;

    public WorkflowSchedule() {
    }

    public WorkflowSchedule(
            String name, String displayName, String cronExpression, String zoneId, Boolean enabled,
            StartWorkflowRequest startWorkflowRequest, Instant nextRunAt) {
        this.name = name;
        this.displayName = displayName;
        this.cronExpression = cronExpression;
        this.zoneId = zoneId;
        this.enabled = enabled;
        this.startWorkflowRequest = startWorkflowRequest;
        this.nextRunAt = nextRunAt;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static boolean isReservedName(String name) {
        return "preview".equals(name) || "new".equals(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public StartWorkflowRequest getStartWorkflowRequest() {
        return startWorkflowRequest;
    }

    public void setStartWorkflowRequest(StartWorkflowRequest startWorkflowRequest) {
        this.startWorkflowRequest = startWorkflowRequest;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public Instant getNextRunAt() {
        return nextRunAt;
    }
}
