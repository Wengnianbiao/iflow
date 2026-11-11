package com.helianhealth.iflow.persistence.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowSchedule;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlWorkflowScheduleDAOTest {

    private static final Instant DUE = Instant.parse("2026-11-10T00:00:00Z");
    private MySqlWorkflowScheduleDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("../docs/init_mysql/04_workflow_schedule.sql")));
        }
        dao = new MySqlWorkflowScheduleDAO(dataSource, new ObjectMapper());
    }

    @Test
    void roundTripsConfigurationAndListsOnlyDueSchedulesInTimeThenNameOrder() {
        WorkflowSchedule first = dao.create(schedule("m_sync", "第一次", true, DUE));
        WorkflowSchedule earlier = dao.create(schedule("a_sync", "更早", true, DUE.minusSeconds(1)));
        WorkflowSchedule sameTime = dao.create(schedule("z_sync", "同时", true, DUE));
        dao.create(schedule("future_sync", "未来", true, DUE.plusSeconds(1)));
        dao.create(schedule("paused_sync", "停用", false, null));

        assertEquals(5, dao.findAll().size());
        assertEquals(List.of("a_sync", "m_sync", "z_sync"),
                dao.findDue(DUE, 3).stream().map(WorkflowSchedule::getName).toList());
        assertEquals(List.of(earlier.getName(), first.getName()),
                dao.findDue(DUE, 2).stream().map(WorkflowSchedule::getName).toList());
        WorkflowSchedule loaded = dao.findByName(first.getName()).orElseThrow();
        assertEquals("第一次", loaded.getDisplayName());
        assertEquals(first.getCronExpression(), loaded.getCronExpression());
        assertEquals(first.getZoneId(), loaded.getZoneId());
        assertEquals(first.getStartWorkflowRequest(), loaded.getStartWorkflowRequest());
        assertEquals(DUE, loaded.getNextRunAt());
        assertTrue(dao.advance(loaded, DUE.plusSeconds(60)));
        assertEquals(DUE.plusSeconds(60), dao.findByName(loaded.getName()).orElseThrow().getNextRunAt());
    }

    @Test
    void sameDisplayNameIsAllowedAndDuplicateIdentifierIsRejected() {
        dao.create(schedule("sync_lab_results", "每天同步检验结果", true, DUE));
        dao.create(schedule("other_job", "每天同步检验结果", true, DUE));
        assertThrows(IllegalArgumentException.class,
                () -> dao.create(schedule("sync_lab_results", "另一条", true, DUE)));

        WorkflowSchedule other = dao.findByName("other_job").orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> dao.update(other, schedule("sync_lab_results", "另一条", true, DUE)));
        assertEquals("other_job", dao.findByName("other_job").orElseThrow().getName());
    }

    @Test
    void onlyOneConcurrentClaimCommitsAndAStaleEditCannotRestoreTheCursor() throws Exception {
        WorkflowSchedule schedule = dao.create(schedule("sync_lab_results", "每天同步检验结果", true, DUE));
        Instant next = DUE.plusSeconds(60);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return dao.advance(schedule, next);
            });
            var second = executor.submit(() -> {
                start.await();
                return dao.advance(schedule, next);
            });
            start.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS) ^ second.get(5, TimeUnit.SECONDS));
        }

        assertTrue(dao.update(schedule, schedule("sync_lab_results", "每天同步检验结果", true, DUE)).isEmpty());
        assertEquals(next, dao.findByName(schedule.getName()).orElseThrow().getNextRunAt());
        assertTrue(dao.findDue(DUE, 10).isEmpty());
    }

    @Test
    void displayNameChangeKeepsTheClaimAndExecutionChangesInvalidateIt() {
        WorkflowSchedule original = dao.create(schedule("sync_lab_results", "每天同步检验结果", true, DUE));
        WorkflowSchedule renamedDisplay = dao.update(
                original, schedule("sync_lab_results", "新的展示名称", true, DUE)).orElseThrow();
        assertEquals("新的展示名称", renamedDisplay.getDisplayName());
        assertTrue(dao.advance(original, DUE.plusSeconds(30)));

        WorkflowSchedule claimed = dao.findByName(original.getName()).orElseThrow();
        WorkflowSchedule edited = dao.update(claimed, schedule(
                "sync_lab_results", "新的展示名称", true, claimed.getNextRunAt(), changedRequest())).orElseThrow();
        assertFalse(dao.advance(claimed, DUE.plusSeconds(90)));

        WorkflowSchedule disabled = dao.update(
                edited, schedule("sync_lab_results", "新的展示名称", false, null, changedRequest())).orElseThrow();
        assertNull(disabled.getNextRunAt());
        assertFalse(dao.advance(edited, DUE.plusSeconds(90)));

        WorkflowSchedule reenabled = dao.update(
                disabled, schedule("sync_lab_results", "新的展示名称", true, DUE, changedRequest())).orElseThrow();
        WorkflowSchedule renamed = dao.update(
                reenabled, schedule("renamed_job", "新名称", true, DUE, changedRequest())).orElseThrow();
        assertEquals("renamed_job", renamed.getName());
        assertTrue(dao.findByName("sync_lab_results").isEmpty());
        assertFalse(dao.advance(reenabled, DUE.plusSeconds(60)));
        dao.delete(renamed.getName());
        assertTrue(dao.findByName(renamed.getName()).isEmpty());
        assertFalse(dao.advance(renamed, DUE.plusSeconds(60)));
    }

    private static WorkflowSchedule schedule(String name, String displayName, boolean enabled, Instant nextRunAt) {
        return schedule(name, displayName, enabled, nextRunAt, new StartWorkflowRequest(
                "lis/sync-results", null, Map.of("nested", List.of(Map.of("source", "LIS")))));
    }

    private static WorkflowSchedule schedule(
            String name, String displayName, boolean enabled, Instant nextRunAt, StartWorkflowRequest request) {
        return new WorkflowSchedule(
                name, displayName, "0 * * * * *", "Asia/Shanghai", enabled, request, nextRunAt);
    }

    private static StartWorkflowRequest changedRequest() {
        return new StartWorkflowRequest("lis/sync-results", 1, Map.of("source", "NEW"));
    }
}
