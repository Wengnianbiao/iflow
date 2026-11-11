package com.helianhealth.iflow.gateway.schedule;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowSchedule;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlWorkflowScheduleDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 定时计划管理与工作流触发。 */
public class WorkflowScheduleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowScheduleService.class);
    private static final int PREVIEW_LIMIT = 5;

    private final MySqlWorkflowScheduleDAO scheduleDAO;
    private final MetadataService metadataService;
    private final WorkflowExecutionService workflowExecutionService;

    public WorkflowScheduleService(
            MySqlWorkflowScheduleDAO scheduleDAO,
            MetadataService metadataService,
            WorkflowExecutionService workflowExecutionService) {
        this.scheduleDAO = scheduleDAO;
        this.metadataService = metadataService;
        this.workflowExecutionService = workflowExecutionService;
    }

    public List<WorkflowSchedule> findAll() {
        return scheduleDAO.findAll();
    }

    public Optional<WorkflowSchedule> findByName(String name) {
        return scheduleDAO.findByName(name);
    }

    public List<Long> preview(String cronExpression, String zoneId) {
        CronExpression cron = parseCronExpression(cronExpression);
        ZonedDateTime next = requireNextRun(cron, Instant.now().atZone(parseZoneId(zoneId)));
        List<Long> times = new ArrayList<>(PREVIEW_LIMIT);
        times.add(next.toInstant().toEpochMilli());
        for (int i = 1; i < PREVIEW_LIMIT; i++) {
            next = cron.next(next);
            if (next == null) {
                break;
            }
            times.add(next.toInstant().toEpochMilli());
        }
        return times;
    }

    public WorkflowSchedule create(WorkflowSchedule schedule) {
        validate(schedule);
        return scheduleDAO.create(prepareForSave(schedule, Instant.now()));
    }

    public Optional<WorkflowSchedule> update(String name, WorkflowSchedule schedule) {
        validate(schedule);
        while (true) {
            Optional<WorkflowSchedule> current = scheduleDAO.findByName(name);
            if (current.isEmpty()) {
                return Optional.empty();
            }
            WorkflowSchedule replacement = prepareForSave(schedule, Instant.now());
            Optional<WorkflowSchedule> saved = scheduleDAO.update(current.get(), replacement);
            if (saved.isPresent()) {
                return saved;
            }
            // 领取或保存已改变下一次触发时间，重新读取后再计算。
        }
    }

    public void delete(String name) {
        scheduleDAO.delete(name);
    }

    void trigger(WorkflowSchedule schedule, Instant readyAt, Instant now) {
        Instant scheduledAt = schedule.getNextRunAt();
        if (scheduledAt.isAfter(now)) {
            return;
        }
        try {
            boolean missedBeforeStartup = !scheduledAt.isAfter(readyAt);
            Instant nextRunAt = nextRunAt(schedule, missedBeforeStartup ? readyAt : now);
            boolean advanced = scheduleDAO.advance(schedule, nextRunAt);
            if (!advanced || missedBeforeStartup) {
                return;
            }

            String workflowId = workflowExecutionService.startWorkflow(schedule.getStartWorkflowRequest());
            LOGGER.info("Started scheduled workflow: scheduleName={}, scheduledAt={}, workflowId={}",
                    schedule.getName(), scheduledAt, workflowId);
        } catch (RuntimeException error) {
            LOGGER.error("Failed to trigger scheduled workflow: scheduleName={}, scheduledAt={}",
                    schedule.getName(), scheduledAt, error);
        }
    }

    private void validate(WorkflowSchedule schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("定时计划不能为空");
        }
        String name = schedule.getName() == null ? "" : schedule.getName().trim();
        schedule.setName(name);
        if (!WorkflowSchedule.isValidName(name)) {
            throw new IllegalArgumentException("计划名称只能包含英文字母、数字和下划线，且长度不得超过 255 个字符");
        }
        if (WorkflowSchedule.isReservedName(name)) {
            throw new IllegalArgumentException("计划名称不能使用 preview 或 new");
        }
        String displayName = schedule.getDisplayName() == null ? "" : schedule.getDisplayName().trim();
        schedule.setDisplayName(displayName);
        if (!StringUtils.hasText(displayName) || displayName.codePointCount(0, displayName.length()) > 255) {
            throw new IllegalArgumentException("展示名称不能为空且长度不得超过 255 个字符");
        }
        if (schedule.getEnabled() == null) {
            throw new IllegalArgumentException("启用状态 enabled 不能为空");
        }
        StartWorkflowRequest request = schedule.getStartWorkflowRequest();
        if (request == null || !WorkflowDef.isValidRoute(request.getRoute())) {
            throw new IllegalArgumentException("工作流路由 route 格式错误");
        }
        if (request.getVersion() != null && request.getVersion() <= 0) {
            throw new IllegalArgumentException("工作流版本 version 必须为正整数");
        }
        metadataService.getWorkflowDef(request.getRoute(), request.getVersion());
    }

    private void validateText(String value, String field) {
        if (!StringUtils.hasText(value) || value.codePointCount(0, value.length()) > 255) {
            throw new IllegalArgumentException(field + "不能为空且长度不得超过 255 个字符");
        }
    }

    private WorkflowSchedule prepareForSave(WorkflowSchedule schedule, Instant now) {
        Instant nextRunAt = nextRunAt(schedule, now);
        StartWorkflowRequest request = schedule.getStartWorkflowRequest();
        if (request.getInput() == null) {
            request = new StartWorkflowRequest(request.getRoute(), request.getVersion(), Map.of());
        }
        return new WorkflowSchedule(
                schedule.getName(), schedule.getDisplayName(), schedule.getCronExpression(), schedule.getZoneId(),
                schedule.getEnabled(), request, schedule.getEnabled() ? nextRunAt : null);
    }

    private Instant nextRunAt(WorkflowSchedule schedule, Instant after) {
        CronExpression cron = parseCronExpression(schedule.getCronExpression());
        ZoneId zone = parseZoneId(schedule.getZoneId());
        return requireNextRun(cron, after.atZone(zone)).toInstant();
    }

    private CronExpression parseCronExpression(String expression) {
        validateText(expression, "Cron 表达式");
        try {
            return CronExpression.parse(expression);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Cron 表达式格式错误", error);
        }
    }

    private ZoneId parseZoneId(String zoneId) {
        validateText(zoneId, "时区");
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("时区格式错误", error);
        }
    }

    private ZonedDateTime requireNextRun(CronExpression cron, ZonedDateTime after) {
        ZonedDateTime next = cron.next(after);
        if (next == null) {
            throw new IllegalArgumentException("Cron 表达式没有未来触发时点");
        }
        return next;
    }
}
