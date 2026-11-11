package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.base.ResultData;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowSchedule;
import com.helianhealth.iflow.console.config.RequestMappingConstants;
import com.helianhealth.iflow.gateway.schedule.WorkflowScheduleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@ConditionalOnBean(WorkflowScheduleService.class)
@RequestMapping(RequestMappingConstants.WORKFLOW_SCHEDULES)
public class WorkflowScheduleController {

    private final WorkflowScheduleService scheduleService;

    public WorkflowScheduleController(WorkflowScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ResultData<List<WorkflowSchedule>> list() {
        return ResultData.ok(scheduleService.findAll());
    }

    @PostMapping
    public ResultData<WorkflowSchedule> create(@RequestBody WorkflowSchedule schedule) {
        return ResultData.ok(scheduleService.create(schedule));
    }

    @GetMapping("/preview")
    public ResultData<List<Long>> preview(
            @RequestParam("cronExpression") String cronExpression,
            @RequestParam("zoneId") String zoneId) {
        return ResultData.ok(scheduleService.preview(cronExpression, zoneId));
    }

    @GetMapping("/{name}")
    public ResultData<WorkflowSchedule> get(@PathVariable("name") String name) {
        String scheduleName = parseName(name);
        return ResultData.ok(scheduleService.findByName(scheduleName)
                .orElseThrow(() -> scheduleNotFound(scheduleName)));
    }

    @PutMapping("/{name}")
    public ResultData<WorkflowSchedule> update(
            @PathVariable("name") String name, @RequestBody WorkflowSchedule schedule) {
        String scheduleName = parseName(name);
        return ResultData.ok(scheduleService.update(scheduleName, schedule)
                .orElseThrow(() -> scheduleNotFound(scheduleName)));
    }

    @DeleteMapping("/{name}")
    public ResultData<Boolean> delete(@PathVariable("name") String name) {
        scheduleService.delete(parseName(name));
        return ResultData.ok(true);
    }

    private String parseName(String name) {
        if (!WorkflowSchedule.isValidName(name)) {
            throw new IllegalArgumentException("计划名称只能包含英文字母、数字和下划线，且长度不得超过 255 个字符");
        }
        if (WorkflowSchedule.isReservedName(name)) {
            throw new IllegalArgumentException("计划名称不能使用 preview 或 new");
        }
        return name;
    }

    private ResponseStatusException scheduleNotFound(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "定时计划不存在: " + name);
    }
}
