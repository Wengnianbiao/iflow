package com.helianhealth.iflow.persistence.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.common.domain.statistics.ExecutionStatistics;
import com.helianhealth.iflow.common.domain.run.ExecutionSearch;
import com.helianhealth.iflow.core.config.IflowCoreConfiguration;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.StartWorkflowInput;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.tasks.DoWhile;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionIndexIntegrationTest {
    private JdbcDataSource dataSource;
    private MySqlIndexDAO statistics;
    private MySqlExecutionDAO executions;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        var registry = new SystemTaskRegistry(Set.of(new WorkflowSystemTask("HTTP") {
            @Override public boolean isAsync() { return true; }
        }, new WorkflowSystemTask("INLINE") {}));
        statistics = new MySqlIndexDAO(dataSource, mapper, registry);
        executions = new MySqlExecutionDAO(dataSource, mapper, statistics);
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../docs/init_mysql/02_schema.sql"));
        }
    }

    @Test
    void overviewMergesVersionsAndAllTaskTypesWithWeightedValidDurations() throws Exception {
        save(workflow("a1", "a", WorkflowModel.Status.COMPLETED, 100, 110));
        var second = workflow("a2", "a", WorkflowModel.Status.FAILED, 100, 130);
        second.getWorkflowDefinition().setVersion(2);
        save(second);
        save(workflow("b1", "b", WorkflowModel.Status.TIMED_OUT, 100, 300));
        save(workflow("b2", "b", WorkflowModel.Status.RUNNING, 100, 0));
        save(workflow("invalid", "a", WorkflowModel.Status.TERMINATED, 200, 100));
        save(task("http", "a1", "a", "HTTP", "call", TaskModel.Status.FAILED, 100, 120));
        var retry = task("retry", "a1", "a", "HTTP", "call", TaskModel.Status.COMPLETED, 150, 180);
        retry.setRetryCount(1);
        retry.setIteration(2);
        save(retry);
        save(task("inline", "a1", "a", "INLINE", "script", TaskModel.Status.COMPLETED_WITH_ERRORS, 100, 105));
        save(task("unknown", "a1", "a", "OLD_TYPE", "old", TaskModel.Status.SKIPPED, 100, 100));
        var queued = task("queued", "a1", "a", "HTTP", "wait", TaskModel.Status.SCHEDULED, 100, 0);
        queued.setStartTime(0);
        save(queued);

        var result = statistics.overview(new ExecutionStatistics.OverviewQuery(0, 1000, "", "UTC"));
        assertEquals(5, result.getWorkflows().totalCount());
        assertEquals(1, result.getWorkflows().runningCount());
        assertEquals(3, result.getWorkflows().failureCount());
        assertEquals(3, result.getWorkflows().durationCount());
        assertEquals(80.0, result.getWorkflows().avgDurationMs());
        assertEquals(0.25, result.getWorkflows().successRate());
        assertEquals(5, result.getTasks().totalCount());
        assertEquals(1, result.getTasks().runningCount());
        assertEquals(3, result.getTasks().successCount());
        assertEquals(0.75, result.getTasks().successRate());
        assertEquals(13.75, result.getTasks().avgDurationMs());
        assertEquals(5, result.getTrend().getFirst().getTaskCount());

        var filtered = statistics.overview(new ExecutionStatistics.OverviewQuery(0, 1000, "a", "UTC"));
        assertEquals(3, filtered.getWorkflows().totalCount());
        assertEquals(20.0, filtered.getWorkflows().avgDurationMs());
        assertEquals(5, filtered.getTasks().totalCount());
        assertEquals(3, filtered.getTrend().getFirst().getWorkflowCount());
    }

    @Test
    void overviewClipsPartialHoursIncludesEndpointsAndFillsMissingBuckets() throws Exception {
        var start = ZonedDateTime.parse("2026-10-30T09:30:00+08:00[Asia/Shanghai]");
        long from = start.toInstant().toEpochMilli();
        long boundary = start.withHour(11).withMinute(0).toInstant().toEpochMilli();
        long to = start.withHour(12).withMinute(15).toInstant().toEpochMilli();
        save(workflow("first", "a", WorkflowModel.Status.RUNNING, from, 0));
        save(workflow("boundary", "a", WorkflowModel.Status.RUNNING, boundary, 0));
        save(workflow("last", "a", WorkflowModel.Status.RUNNING, to, 0));
        save(workflow("before", "a", WorkflowModel.Status.RUNNING, from - 1, 0));
        save(workflow("after", "a", WorkflowModel.Status.RUNNING, to + 1, 0));
        save(task("last-task", "last", "a", "INLINE", "script", TaskModel.Status.COMPLETED, to, to));
        var overview = statistics.overview(new ExecutionStatistics.OverviewQuery(from, to, "a", "Asia/Shanghai"));
        assertEquals("hour", overview.getGranularity());
        assertEquals(3, overview.getWorkflows().totalCount());
        assertEquals(4, overview.getTrend().size());
        assertEquals(start.withMinute(0).toInstant().toEpochMilli(), overview.getTrend().getFirst().getStartTime());
        assertEquals(List.of(1L, 0L, 1L, 1L), overview.getTrend().stream()
                .map(ExecutionStatistics.TrendPoint::getWorkflowCount).toList());
        assertEquals(List.of(0L, 0L, 0L, 1L), overview.getTrend().stream()
                .map(ExecutionStatistics.TrendPoint::getTaskCount).toList());
    }

    @Test
    void overviewUsesLocalCalendarDaysAndMonthsAndReturnsEmptyMetrics() {
        var start = ZonedDateTime.parse("2026-03-07T00:00:00-05:00[America/New_York]");
        var daily = statistics.overview(new ExecutionStatistics.OverviewQuery(start.toInstant().toEpochMilli(),
                start.plusDays(3).toInstant().toEpochMilli() - 1, "", "America/New_York"));
        assertEquals("day", daily.getGranularity());
        assertEquals(3, daily.getTrend().size());
        for (int i = 0; i < 3; i++) {
            assertEquals(start.plusDays(i).toInstant().toEpochMilli(), daily.getTrend().get(i).getStartTime());
            assertEquals(0, daily.getTrend().get(i).getWorkflowCount());
            assertEquals(0, daily.getTrend().get(i).getTaskCount());
        }
        assertNull(daily.getWorkflows().avgDurationMs());
        assertNull(daily.getTasks().successRate());
        assertEquals(0, daily.getWorkflows().totalCount());
        var monthly = statistics.overview(new ExecutionStatistics.OverviewQuery(start.toInstant().toEpochMilli(),
                start.plusMonths(4).toInstant().toEpochMilli(), "", "America/New_York"));
        assertEquals("month", monthly.getGranularity());
        assertEquals(5, monthly.getTrend().size());
        assertEquals(start.withDayOfMonth(1).toInstant().toEpochMilli(), monthly.getTrend().getFirst().getStartTime());
        assertEquals("hour", statistics.overview(new ExecutionStatistics.OverviewQuery(0,
                Duration.ofDays(2).toMillis() - 1, "", "UTC")).getGranularity());
        assertEquals("day", statistics.overview(new ExecutionStatistics.OverviewQuery(0,
                Duration.ofDays(90).toMillis() - 1, "", "UTC")).getGranularity());
    }

    @Test
    void overviewKeepsBothHoursWhenDaylightSavingTimeEnds() throws Exception {
        var start = ZonedDateTime.parse("2026-11-01T00:00:00-04:00[America/New_York]");
        long firstHour = start.plusHours(1).toInstant().toEpochMilli();
        long secondHour = start.plusHours(2).toInstant().toEpochMilli();
        save(workflow("first-hour", "a", WorkflowModel.Status.RUNNING, firstHour, 0));
        save(workflow("second-hour", "a", WorkflowModel.Status.RUNNING, secondHour, 0));
        var result = statistics.overview(new ExecutionStatistics.OverviewQuery(start.toInstant().toEpochMilli(),
                start.plusDays(1).toInstant().toEpochMilli() - 1, "", "America/New_York"));
        assertEquals(25, result.getTrend().size());
        assertEquals(firstHour, result.getTrend().get(1).getStartTime());
        assertEquals(secondHour, result.getTrend().get(2).getStartTime());
        assertEquals(1, result.getTrend().get(1).getWorkflowCount());
        assertEquals(1, result.getTrend().get(2).getWorkflowCount());
    }

    @Test
    void manualAndRestartTerminationsLeavePendingAndCountAsFailures() {
        for (var status : List.of(WorkflowModel.Status.TERMINATED, WorkflowModel.Status.INTERRUPTED)) {
            String id = status.name();
            var workflow = workflow(id, "test", WorkflowModel.Status.RUNNING, 100, 0);
            executions.createWorkflow(workflow);
            assertEquals(List.of(id), executions.getUnfinishedWorkflowIds());
            assertEquals(List.of(id), executions.getRunningWorkflowIds());
            assertEquals(List.of(id), executions.getRunningWorkflowIds("test", 1));
            workflow.setStatus(status);
            executions.updateWorkflow(workflow);
            assertTrue(executions.getUnfinishedWorkflowIds().isEmpty());
            assertTrue(executions.getRunningWorkflowIds("test", 1).isEmpty());
        }
        var summary = statistics.workflows(query(false, "totalCount", 1, 20)).summary();
        assertEquals(2, summary.failureCount());
        assertEquals(0, summary.runningCount());
    }

    @Test
    void wiresWithDoWhileWithoutCircularReferencesAndClassifiesTaskWrites() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("agent.repository.vendor", "mysql")));
            context.registerBean(DataSource.class, () -> dataSource);
            context.registerBean(ObjectMapper.class, () -> mapper);
            context.register(MySqlExecutionDAO.class, MySqlIndexDAO.class,
                    ExecutionDAOFacade.class, ParametersUtils.class, DoWhile.class, SystemTaskRegistry.class);
            context.registerBean("customAsyncTask", WorkflowSystemTask.class,
                    () -> new WorkflowSystemTask("CUSTOM_ASYNC") {
                        @Override public boolean isAsync() { return true; }
                    });
            context.refresh();

            assertSame(context.getBean(DoWhile.class),
                    context.getBean(SystemTaskRegistry.class).get("DO_WHILE"));
            var executionDAO = context.getBean(MySqlExecutionDAO.class);
            var statisticsDAO = context.getBean(MySqlIndexDAO.class);
            executionDAO.createWorkflow(workflow("wiring", "wiring", WorkflowModel.Status.RUNNING, 100, 0));
            executionDAO.createTasks(java.util.List.of(
                    task("loop", "wiring", "wiring", "DO_WHILE", "loop",
                            TaskModel.Status.SCHEDULED, 100, 0),
                    task("async", "wiring", "wiring", "CUSTOM_ASYNC", "async",
                            TaskModel.Status.SCHEDULED, 100, 0)));

            assertEquals(2, statisticsDAO.tasks(query(false, "totalCount", 1, 20)).summary().totalCount());
            var async = statisticsDAO.tasks(query(true, "totalCount", 1, 20));
            assertEquals(1, async.summary().totalCount());
            assertEquals("CUSTOM_ASYNC", async.rows().getFirst().taskType());
        }
    }

    @Test
    void aggregatesAllTerminalStatesAndWeightsSummaryAcrossPages() throws Exception {
        save(workflow("a1", "a", WorkflowModel.Status.COMPLETED, 100, 110));
        save(workflow("a2", "a", WorkflowModel.Status.FAILED, 100, 130));
        save(workflow("b1", "b", WorkflowModel.Status.TIMED_OUT, 100, 300));
        save(workflow("b2", "b", WorkflowModel.Status.RUNNING, 100, 0));
        var page = statistics.workflows(query(false, "avgDurationMs", 1, 1));
        assertEquals(2, page.total());
        assertEquals("b", page.rows().getFirst().workflowRoute());
        assertEquals(4, page.summary().totalCount());
        assertEquals(1, page.summary().runningCount());
        assertEquals(1.0 / 3, page.summary().successRate());
        assertEquals(80.0, page.summary().avgDurationMs());
        assertEquals(10L, page.summary().minDurationMs());
        assertEquals(200L, page.summary().maxDurationMs());
        assertEquals("a", statistics.workflows(query(false, "avgDurationMs", 2, 1)).rows().getFirst().workflowRoute());
        for (String sort : java.util.List.of("successRate", "minDurationMs", "maxDurationMs", "totalCount")) {
            assertEquals(2, statistics.workflows(new ExecutionStatistics.Query(0, 1000, "", false, sort, false, 1, 20)).rows().size());
        }
    }

    @Test
    void zeroAndMissingTimesAreNotConfusedAndEmptySuccessRateIsNull() throws Exception {
        save(workflow("zero", "a", WorkflowModel.Status.COMPLETED, 100, 100));
        save(workflow("invalid", "a", WorkflowModel.Status.TERMINATED, 200, 100));
        var summary = statistics.workflows(query(false, "totalCount", 1, 20)).summary();
        assertEquals(2, summary.totalCount());
        assertEquals(1, summary.durationCount());
        assertEquals(0.0, summary.avgDurationMs());
        var empty = statistics.workflows(new ExecutionStatistics.Query(500, 600, "", false, "totalCount", true, 1, 20));
        assertEquals(0, empty.total());
        assertNull(empty.summary().avgDurationMs());
        assertNull(empty.summary().successRate());
    }

    @Test
    void groupsAttemptsByDefinitionNodeAndFiltersAsyncWithoutMergingWorkflows() throws Exception {
        save(workflow("wf-a", "a", WorkflowModel.Status.RUNNING, 100, 0));
        save(workflow("wf-b", "b", WorkflowModel.Status.RUNNING, 100, 0));
        var first = task("t1", "wf-a", "a", "HTTP", "lookup__literal", TaskModel.Status.FAILED, 100, 120);
        var retry = task("t2", "wf-a", "a", "HTTP", "lookup__literal", TaskModel.Status.COMPLETED, 130, 160);
        retry.setRetryCount(1);
        retry.setIteration(2);
        retry.setReferenceTaskName("lookup__literal__2");
        save(first); save(retry);
        save(task("t3", "wf-b", "b", "HTTP", "lookup__literal", TaskModel.Status.COMPLETED, 100, 140));
        save(task("t4", "wf-a", "a", "INLINE", "map", TaskModel.Status.COMPLETED_WITH_ERRORS, 100, 105));
        save(task("t5", "wf-a", "a", "OLD_TYPE", "old", TaskModel.Status.SKIPPED, 100, 100));
        var async = statistics.tasks(query(true, "totalCount", 1, 20));
        assertEquals(2, async.total());
        assertEquals(3, async.summary().totalCount());
        assertEquals("lookup__literal", async.rows().getFirst().nodeRef());
        assertEquals(2, async.rows().getFirst().metrics().totalCount());
        assertEquals(25.0, async.rows().getFirst().metrics().avgDurationMs());
        var all = statistics.tasks(query(false, "totalCount", 1, 20));
        assertEquals(5, all.summary().totalCount());
        assertEquals(4, all.summary().successCount());
        var attempts = statistics.taskInstances(new ExecutionStatistics.Query(0, 1000, "a", true,
                "totalCount", true, 1, 1), "lookup__literal", "HTTP");
        assertEquals(2, attempts.total());
        assertEquals("t2", attempts.rows().getFirst().taskId());
        assertEquals(1, attempts.rows().getFirst().retryCount());
    }

    @Test
    void liveWritesUpdateInsteadOfCountingTwiceAndDeleteWithSource() {
        var wf = workflow("live", "a", WorkflowModel.Status.RUNNING, System.currentTimeMillis() - 1000, 0);
        executions.createWorkflow(wf);
        var task = task("live-task", "live", "a", "HTTP", "call", TaskModel.Status.SCHEDULED, 100, 0);
        task.setStartTime(0);
        executions.createTasks(java.util.List.of(task));
        assertEquals(1, statistics.tasks(query(true, "totalCount", 1, 20)).summary().runningCount());
        task.setStartTime(200);
        task.setStatus(TaskModel.Status.COMPLETED);
        executions.updateTask(task);
        executions.updateTask(task);
        wf.setStatus(WorkflowModel.Status.COMPLETED);
        executions.updateWorkflow(wf);
        var window = new ExecutionStatistics.Query(0, Long.MAX_VALUE, "", false, "totalCount", true, 1, 20);
        assertEquals(1, statistics.workflows(window).summary().totalCount());
        assertEquals(wf.getEndTime() - wf.getStartTime(), statistics.workflows(window).summary().avgDurationMs());
        assertEquals(1, statistics.tasks(window).summary().totalCount());
        executions.removeWorkflow("live");
        assertEquals(0, statistics.workflows(window).summary().totalCount());
        assertEquals(0, statistics.tasks(window).summary().totalCount());
    }

    @Test
    void projectionFailureRollsBackSourceCreation() throws Exception {
        try (var tx = dataSource.getConnection(); var sql = tx.createStatement()) { sql.execute("DROP TABLE workflow_index"); }
        assertThrows(IllegalStateException.class, () -> executions.createWorkflow(workflow("rollback", "a", WorkflowModel.Status.RUNNING, 100, 0)));
        assertNull(executions.getWorkflow("rollback"));
    }

    @Test
    void workflowSearchCombinesFiltersAndPaginatesInDatabaseWithoutReadingPayloads() throws Exception {
        var first = workflow("first", "a", WorkflowModel.Status.COMPLETED, 10, 20);
        first.setCorrelationId("order"); first.setReasonForInCompletion("诊断信息");
        var second = workflow("second", "b", WorkflowModel.Status.FAILED, 20, 30);
        second.setWorkflowDefinition(WorkflowDef.builder().route("b").name("工作流 b").version(2).build());
        second.setCorrelationId("order"); second.setFailedTaskId("failed-task");
        save(first); save(second);
        // 主表没有相应记录，搜索必须仅从摘要索引完成筛选、排序和分页。
        var page = statistics.searchWorkflows(new ExecutionSearch.WorkflowQuery(List.of("a", "b", "a"),
                null, List.of(), null, "order", 0L, 100L, 1, 1));
        assertEquals(2, page.getTotal());
        assertEquals("second", page.getRows().getFirst().workflowId());
        assertEquals("failed-task", page.getRows().getFirst().failedTaskId());
        var exact = statistics.searchWorkflows(new ExecutionSearch.WorkflowQuery(List.of("b"), 2,
                List.of("failed"), "second", "order", 20L, 20L, 1, 20));
        assertEquals(1, exact.getTotal());
        assertEquals(0, statistics.searchWorkflows(new ExecutionSearch.WorkflowQuery(List.of("a"),
                null, List.of(), "second", null, null, null, 1, 20)).getTotal());
        assertEquals(0, statistics.searchWorkflows(new ExecutionSearch.WorkflowQuery(List.of(),
                null, List.of(), "' OR 1=1 --", null, null, null, 1, 20)).getTotal());
    }

    @Test
    void genericTaskSearchPreservesQueuedStartTimeAndFiltersAttempts() throws Exception {
        var queued = task("queued", "wf", "a", "HTTP", "call", TaskModel.Status.SCHEDULED, 100, 0);
        queued.setStartTime(0);
        statistics.indexTask(queued);
        save(task("sync", "wf", "a", "INLINE", "map", TaskModel.Status.COMPLETED, 100, 105));
        var query = new ExecutionSearch.TaskQuery(List.of("a"), "wf", null, null, "call", "HTTP", List.of("scheduled"),
                true, 100L, 100L, 1, 20);
        var page = statistics.searchTasks(query);
        assertEquals(1, page.getTotal());
        assertEquals("queued", page.getRows().getFirst().taskId());
        assertEquals(0, page.getRows().getFirst().startTime());
        assertEquals(100, page.getRows().getFirst().scheduledTime());
        assertTrue(page.getRows().getFirst().async());
        statistics.removeTask("queued");
        assertEquals(0, statistics.searchTasks(query).getTotal());
    }

    @Test
    void taskSearchJoinsWorkflowKeywordsAndCombinesAsyncRoutesStatusTimeAndPagination() throws Exception {
        var first = workflow("wf-first", "a", WorkflowModel.Status.FAILED, 1, 10);
        first.setCorrelationId("order & 1");
        var second = workflow("wf-second", "b", WorkflowModel.Status.COMPLETED, 2, 20);
        second.setCorrelationId("order & 1");
        var other = workflow("wf-other", "a", WorkflowModel.Status.COMPLETED, 3, 30);
        other.setCorrelationId("other");
        save(first); save(second); save(other);
        save(task("failed", "wf-first", "a", "HTTP", "call", TaskModel.Status.FAILED, 100, 110));
        var retry = task("retry", "wf-first", "a", "HTTP", "call", TaskModel.Status.COMPLETED, 120, 150);
        retry.setRetryCount(1);
        save(retry);
        save(task("second", "wf-second", "b", "HTTP", "call", TaskModel.Status.COMPLETED, 130, 160));
        save(task("sync", "wf-first", "a", "INLINE", "map", TaskModel.Status.COMPLETED, 140, 150));
        save(task("unknown", "wf-first", "a", "OLD_TYPE", "old", TaskModel.Status.SKIPPED, 150, 160));
        save(task("other", "wf-other", "a", "HTTP", "call", TaskModel.Status.COMPLETED, 160, 170));

        var query = new ExecutionSearch.TaskQuery(List.of("a", "b"), null, "order & 1", null, null,
                null, List.of("completed"), true, 120L, 130L, 1, 1);
        var page = statistics.searchTasks(query);
        assertEquals(2, page.getTotal());
        assertEquals("second", page.getRows().getFirst().taskId());
        assertEquals("工作流 b", page.getRows().getFirst().workflowName());
        assertEquals("order & 1", page.getRows().getFirst().correlationId());
        var next = statistics.searchTasks(new ExecutionSearch.TaskQuery(query.workflowRoutes(), null,
                query.correlationId(), null, null, null, query.statuses(), true, 120L, 130L, 2, 1));
        assertEquals(2, next.getTotal());
        assertEquals("retry", next.getRows().getFirst().taskId());
        assertEquals(1, next.getRows().getFirst().retryCount());
        assertEquals(5, statistics.searchTasks(new ExecutionSearch.TaskQuery(List.of(), null,
                "order & 1", null, null, null, List.of(), false, null, null, 1, 20)).getTotal());
        assertEquals(0, statistics.searchTasks(new ExecutionSearch.TaskQuery(List.of("b"), null,
                "other", null, null, null, List.of(), false, null, null, 1, 20)).getTotal());
        assertEquals(0, statistics.searchTasks(new ExecutionSearch.TaskQuery(List.of(), null,
                "' OR 1=1 --", null, null, null, List.of(), false, null, null, 1, 20)).getTotal());

        first.setCorrelationId("updated");
        save(first);
        var updated = statistics.searchTasks(new ExecutionSearch.TaskQuery(List.of(), null,
                "updated", null, null, null, List.of(), true, null, null, 1, 20));
        assertEquals(2, updated.getTotal());
        assertTrue(updated.getRows().stream().allMatch(row -> "updated".equals(row.correlationId())));
    }

    @ParameterizedTest
    @CsvSource({"orderId,initial,4821,COMPLETED", "orderId,,4821,COMPLETED", "missing,initial,initial,FAILED"})
    void setsWorkflowKeywordFromTaskOutputAndUpdatesSearch(
            String field, String initialKeyword, String expectedKeyword, WorkflowModel.Status expectedStatus) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "agent.repository.vendor", "mysql", "iflow.workflow-execution.sweeper.enabled", false)));
            context.registerBean(DataSource.class, () -> dataSource);
            context.registerBean(ObjectMapper.class, () -> mapper);
            context.register(IflowCoreConfiguration.class, MySqlExecutionDAO.class,
                    MySqlIndexDAO.class, MySqlMetadataDAO.class);
            context.refresh();

            var lookup = WorkflowTask.builder().name("lookup").taskReferenceName("lookup").type("INLINE")
                    .inputParameters(Map.of("evaluatorType", "graaljs", "expression", "({orderId: 4821})"))
                    .build();
            var setKeyword = WorkflowTask.builder().name("keyword").taskReferenceName("keyword").type("SET_KEYWORD")
                    .inputParameters(Map.of("keyword", "${lookup.output." + field + "}"))
                    .build();
            var definition = WorkflowDef.builder().route("set-keyword").name("设置关键字").version(1)
                    .correlationExpression(initialKeyword).tasks(List.of(lookup, setKeyword))
                    .outputParameters(Map.of("keyword", "${workflow.correlationId}"))
                    .build();
            context.getBean(MetadataService.class).registerWorkflowDef(definition);
            var input = new StartWorkflowInput();
            input.setRoute(definition.getRoute());
            input.setVersion(definition.getVersion());

            String id = context.getBean(WorkflowExecutor.class).startWorkflow(input);

            var stored = executions.getWorkflow(id, true).orElseThrow();
            assertEquals(expectedStatus, stored.getStatus());
            assertEquals(expectedKeyword, stored.getCorrelationId());
            assertEquals(Map.of("keyword", expectedKeyword), stored.getOutput());
            var view = statistics.searchWorkflows(new ExecutionSearch.WorkflowQuery(List.of(), null,
                    List.of(), id, expectedKeyword, null, null, 1, 20));
            assertEquals(1, view.getTotal());
            var tasks = statistics.searchTasks(new ExecutionSearch.TaskQuery(List.of(), id, expectedKeyword,
                    null, null, null, List.of(), false, null, null, 1, 20));
            assertEquals(2, tasks.getTotal());
            assertTrue(tasks.getRows().stream().allMatch(row -> expectedKeyword.equals(row.correlationId())));
            if (expectedStatus == WorkflowModel.Status.FAILED) {
                assertTrue(stored.getTaskByRefName("keyword").getReasonForInCompletion().contains("keyword"));
            } else if (initialKeyword != null) {
                assertEquals(0, statistics.searchWorkflows(new ExecutionSearch.WorkflowQuery(List.of(), null,
                        List.of(), id, initialKeyword, null, null, 1, 20)).getTotal());
            }
        }
    }

    @Test
    void indexRejectsOlderWorkflowAndTaskSnapshots() {
        var latest = workflow("ordered", "a", WorkflowModel.Status.COMPLETED, 100, 150);
        latest.setUpdateTime(200);
        statistics.indexWorkflow(latest);
        var stale = workflow("ordered", "a", WorkflowModel.Status.RUNNING, 100, 0);
        stale.setUpdateTime(100);
        statistics.indexWorkflow(stale);
        assertEquals("COMPLETED", statistics.searchWorkflows(new ExecutionSearch.WorkflowQuery(List.of(), null,
                List.of(), "ordered", null, null, null, 1, 20)).getRows().getFirst().status());
        var task = task("ordered-task", "ordered", "a", "HTTP", "call", TaskModel.Status.COMPLETED, 100, 150);
        task.setUpdateTime(200);
        statistics.indexTask(task);
        task.setStatus(TaskModel.Status.IN_PROGRESS); task.setEndTime(0); task.setUpdateTime(100);
        statistics.indexTask(task);
        assertEquals(1, statistics.tasks(query(true, "totalCount", 1, 20)).summary().successCount());
        statistics.removeWorkflow("ordered");
        assertEquals(0, statistics.tasks(query(false, "totalCount", 1, 20)).summary().totalCount());
    }

    @Test
    void rejectsInvalidRangesAndSortExpressions() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionStatistics.Query(10, 1, "", true, "totalCount", true, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> query(false, "total_count; DROP TABLE task", 1, 20));
    }

    private ExecutionStatistics.Query query(boolean async, String sort, int page, int size) {
        return new ExecutionStatistics.Query(0, Long.MAX_VALUE, "", async, sort, true, page, size);
    }

    private void save(WorkflowModel model) throws Exception {
        try (Connection tx = dataSource.getConnection()) { statistics.indexWorkflow(tx, model); }
    }

    private void save(TaskModel model) throws Exception {
        try (Connection tx = dataSource.getConnection()) { statistics.indexTask(tx, model); }
    }

    private WorkflowModel workflow(String id, String route, WorkflowModel.Status status, long start, long end) {
        var workflow = new WorkflowModel();
        workflow.setWorkflowId(id);
        workflow.setWorkflowDefinition(WorkflowDef.builder().route(route).name("工作流 " + route).version(1).build());
        workflow.setStatus(status); workflow.setStartTime(start); workflow.setEndTime(end);
        return workflow;
    }

    private TaskModel task(String id, String workflowId, String route, String type, String ref,
                           TaskModel.Status status, long start, long end) {
        var task = new TaskModel();
        task.setTaskId(id); task.setWorkflowInstanceId(workflowId); task.setWorkflowType(route);
        task.setTaskType(type); task.setTaskDefName("节点 " + ref); task.setReferenceTaskName(ref);
        task.setWorkflowTask(WorkflowTask.builder().taskReferenceName(ref).name("节点 " + ref).type(type).build());
        task.setStatus(status); task.setScheduledTime(start); task.setStartTime(start); task.setEndTime(end);
        return task;
    }
}
