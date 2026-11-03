package com.helianhealth.iflow.persistence.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MySqlMetadataDAOTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement countStatement;

    @Mock
    private PreparedStatement insertStatement;

    @Mock
    private PreparedStatement maxStatement;

    @Mock
    private PreparedStatement latestVersionStatement;

    @Mock
    private PreparedStatement selectStatement;

    @Mock
    private ResultSet countResultSet;

    @Mock
    private ResultSet maxResultSet;

    @Mock
    private ResultSet selectResultSet;

    private MySqlMetadataDAO dao;

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        dao = new MySqlMetadataDAO(dataSource, OBJECT_MAPPER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void negativeTimeoutIsRejectedForCreationAndUpdate(boolean create) throws Exception {
        org.mockito.Mockito.reset(dataSource, connection);
        WorkflowDef definition = workflowDef();
        definition.setTimeoutSeconds(-1);
        var error = assertThrows(WorkFlowConfigException.class, () -> {
            if (create) dao.createWorkflowDef(definition);
            else dao.updateWorkflowDef(definition);
        });
        assertEquals(true, error.getMessage().contains("timeoutSeconds"));
        verify(connection, never()).prepareStatement(startsWith("INSERT"));
    }

    @Test
    void createWorkflowDefInsertsJsonAndRefreshesLatestVersion() throws Exception {
        when(connection.prepareStatement(contains("SELECT COUNT"))).thenReturn(countStatement);
        when(connection.prepareStatement(startsWith("INSERT INTO meta_workflow_def")))
                .thenReturn(insertStatement);
        when(connection.prepareStatement(contains("SELECT MAX(version)")))
                .thenReturn(maxStatement);
        when(connection.prepareStatement(startsWith("UPDATE meta_workflow_def SET latest_version")))
                .thenReturn(latestVersionStatement);

        when(countStatement.executeQuery()).thenReturn(countResultSet);
        when(countResultSet.next()).thenReturn(true);
        when(countResultSet.getObject(1)).thenReturn(0);

        when(maxStatement.executeQuery()).thenReturn(maxResultSet);
        when(maxResultSet.next()).thenReturn(true);
        when(maxResultSet.getInt(1)).thenReturn(0);
        when(maxResultSet.wasNull()).thenReturn(true);

        when(insertStatement.executeUpdate()).thenReturn(1);
        when(latestVersionStatement.executeUpdate()).thenReturn(1);

        dao.createWorkflowDef(workflowDef());

        verify(insertStatement).setString(1, "demo");
        verify(insertStatement).setInt(2, 1);
        verify(latestVersionStatement).setInt(1, 1);
        verify(latestVersionStatement).setString(2, "demo");
        verify(connection).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"retryCount", "retryDelaySeconds", "startDelay"})
    void rejectsNegativePoliciesInsideNestedTasksBeforeAccessingDatabase(String field) {
        org.mockito.Mockito.reset(dataSource, connection);
        WorkflowTask invalid = WorkflowTask.builder().name("invalid").taskReferenceName("nested").build();
        switch (field) {
            case "retryCount" -> invalid.setRetryCount(-1);
            case "retryDelaySeconds" -> invalid.setRetryDelaySeconds(-1);
            case "startDelay" -> invalid.setStartDelay(-1);
        }
        WorkflowTask loop = WorkflowTask.builder().type("DO_WHILE").loopOver(List.of(invalid)).build();
        WorkflowTask fork = WorkflowTask.builder().type("FORK_JOIN").forkTasks(List.of(List.of(loop))).build();
        WorkflowTask branch = WorkflowTask.builder().type("SWITCH")
                .decisionCases(Map.of("yes", List.of(fork))).build();
        WorkflowDef definition = workflowDef();
        definition.setTasks(List.of(branch));
        for (boolean create : List.of(false, true)) {
            var error = assertThrows(WorkFlowConfigException.class, () -> {
                if (create) dao.createWorkflowDef(definition);
                else dao.updateWorkflowDef(definition);
            });
            assertEquals(true, error.getMessage().contains(field));
            assertEquals(true, error.getMessage().contains("nested"));
        }
        org.mockito.Mockito.verifyNoInteractions(dataSource);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "latest", "versions", "allLatest"})
    void workflowReadsUseDatabaseAuditTimesInsteadOfZeroOrStaleJsonTimes(String lookup) throws Exception {
        WorkflowDef def = workflowDef();
        def.setCreateTime(0L);
        def.setUpdateTime(123L);
        def.setCreatedBy("creator");
        def.setUpdatedBy("editor");
        String jsonData = OBJECT_MAPPER.writeValueAsString(def);
        when(connection.prepareStatement(startsWith("SELECT json_data")))
                .thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(selectResultSet);
        when(selectResultSet.next()).thenReturn(true, false);
        when(selectResultSet.getString("json_data")).thenReturn(jsonData);
        when(selectResultSet.getTimestamp("created_on")).thenReturn(new Timestamp(1_700_000_000_000L));
        when(selectResultSet.getTimestamp("modified_on")).thenReturn(new Timestamp(1_700_086_400_000L));

        List<WorkflowDef> definitions = readDefinitions(lookup);

        assertEquals(1, definitions.size());
        WorkflowDef saved = definitions.get(0);
        assertEquals("demo", saved.getRoute());
        assertEquals(1, saved.getVersion());
        assertEquals("演示工作流", saved.getDescription());
        assertEquals(1_700_000_000_000L, saved.getCreateTime());
        assertEquals(1_700_086_400_000L, saved.getUpdateTime());
        assertEquals("creator", saved.getCreatedBy());
        assertEquals("editor", saved.getUpdatedBy());
        verify(connection).prepareStatement(contains("json_data, created_on, modified_on"));
        if (!"allLatest".equals(lookup)) {
            verify(selectStatement).setString(1, "demo");
        }
        if ("version".equals(lookup)) {
            verify(selectStatement).setInt(2, 1);
        }
        verify(connection).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "latest", "versions", "allLatest"})
    void workflowReadsReturnEmptyWhenNoRowsExist(String lookup) throws Exception {
        when(connection.prepareStatement(startsWith("SELECT json_data"))).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(selectResultSet);
        when(selectResultSet.next()).thenReturn(false);

        assertEquals(List.of(), readDefinitions(lookup));

        verify(connection).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"versions", "allLatest"})
    void workflowListsReadEachRowsOwnAuditTimes(String lookup) throws Exception {
        WorkflowDef first = workflowDef();
        WorkflowDef second = workflowDef();
        second.setVersion(2);
        if ("allLatest".equals(lookup)) {
            second.setRoute("another");
        }
        when(connection.prepareStatement(startsWith("SELECT json_data"))).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(selectResultSet);
        when(selectResultSet.next()).thenReturn(true, true, false);
        when(selectResultSet.getString("json_data")).thenReturn(
                OBJECT_MAPPER.writeValueAsString(first), OBJECT_MAPPER.writeValueAsString(second));
        when(selectResultSet.getTimestamp("created_on")).thenReturn(new Timestamp(1000L), new Timestamp(3000L));
        when(selectResultSet.getTimestamp("modified_on")).thenReturn(new Timestamp(2000L), new Timestamp(4000L));

        List<WorkflowDef> definitions = readDefinitions(lookup);

        assertEquals(2, definitions.size());
        assertEquals(1, definitions.get(0).getVersion());
        assertEquals(1000L, definitions.get(0).getCreateTime());
        assertEquals(2000L, definitions.get(0).getUpdateTime());
        assertEquals(2, definitions.get(1).getVersion());
        assertEquals(3000L, definitions.get(1).getCreateTime());
        assertEquals(4000L, definitions.get(1).getUpdateTime());
        verify(connection).commit();
    }

    private List<WorkflowDef> readDefinitions(String lookup) {
        return switch (lookup) {
            case "version" -> dao.getWorkflowDef("demo", 1).stream().toList();
            case "latest" -> dao.getLatestWorkflowDef("demo").stream().toList();
            case "versions" -> dao.getAllVersions("demo");
            case "allLatest" -> dao.getAllLatest();
            default -> throw new IllegalArgumentException(lookup);
        };
    }

    @Test
    void duplicateRouteAndVersionIsRejectedWithoutInserting() throws Exception {
        when(connection.prepareStatement(contains("SELECT COUNT"))).thenReturn(countStatement);
        when(countStatement.executeQuery()).thenReturn(countResultSet);
        when(countResultSet.next()).thenReturn(true);
        when(countResultSet.getObject(1)).thenReturn(1);
        assertThrows(WorkFlowConfigException.class, () -> dao.createWorkflowDef(workflowDef()));
        verify(connection, never()).prepareStatement(startsWith("INSERT"));
        verify(connection).rollback();
    }

    @Test
    void updatingMissingRouteAndVersionDoesNotInsert() throws Exception {
        when(connection.prepareStatement(contains("SELECT COUNT"))).thenReturn(countStatement);
        when(countStatement.executeQuery()).thenReturn(countResultSet);
        when(countResultSet.next()).thenReturn(true);
        when(countResultSet.getObject(1)).thenReturn(0);
        var error = assertThrows(WorkFlowConfigException.class, () -> dao.updateWorkflowDef(workflowDef()));
        assertEquals(WorkFlowErrorCode.WORKFLOW_NOT_FOUND.getCode(), error.getErrorCode());
        verify(connection, never()).prepareStatement(startsWith("INSERT"));
        verify(connection).rollback();
    }

    @Test
    void renamingExistingDefinitionOnlyUpdatesItsRouteAndVersion() throws Exception {
        when(connection.prepareStatement(contains("SELECT COUNT"))).thenReturn(countStatement);
        when(countStatement.executeQuery()).thenReturn(countResultSet);
        when(countResultSet.next()).thenReturn(true);
        when(countResultSet.getObject(1)).thenReturn(1);
        when(connection.prepareStatement(startsWith("UPDATE meta_workflow_def SET json_data")))
                .thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);
        WorkflowDef definition = workflowDef();
        definition.setName("修改后的业务名称");
        WorkflowTask defaulted = WorkflowTask.builder().name("defaulted").taskReferenceName("defaulted").build();
        WorkflowTask explicit = WorkflowTask.builder().name("explicit").taskReferenceName("explicit")
                .retryCount(0).retryDelaySeconds(0).retryLogic(WorkflowTask.RetryLogic.FIXED)
                .startDelay(0).build();
        definition.setTasks(List.of(WorkflowTask.builder().name("fork").taskReferenceName("fork").type("FORK_JOIN")
                .forkTasks(List.of(List.of(defaulted), List.of(explicit))).build()));

        dao.updateWorkflowDef(definition);

        verify(insertStatement).setString(1, OBJECT_MAPPER.writeValueAsString(definition));
        WorkflowDef restored = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(definition), WorkflowDef.class);
        assertEquals(definition.getTasks(), restored.getTasks());
        assertEquals(0, restored.getTaskByRefName("defaulted").getRetryCount());
        assertEquals(0, restored.getTaskByRefName("defaulted").getRetryDelaySeconds());
        assertEquals(WorkflowTask.RetryLogic.FIXED, restored.getTaskByRefName("defaulted").getRetryLogic());
        assertEquals(0, restored.getTaskByRefName("defaulted").getStartDelay());
        assertEquals(0, restored.getTaskByRefName("explicit").getStartDelay());
        verify(insertStatement).setString(2, "demo");
        verify(insertStatement).setInt(3, 1);
        verify(connection, never()).prepareStatement(startsWith("INSERT"));
        verify(connection).commit();
    }

    @Test
    void sameBusinessNameCanBeInsertedUnderDifferentRoutesAndVersions() throws Exception {
        when(connection.prepareStatement(contains("SELECT COUNT"))).thenReturn(countStatement);
        when(connection.prepareStatement(startsWith("INSERT INTO meta_workflow_def"))).thenReturn(insertStatement);
        when(connection.prepareStatement(contains("SELECT MAX(version)"))).thenReturn(maxStatement);
        when(connection.prepareStatement(startsWith("UPDATE meta_workflow_def SET latest_version"))).thenReturn(latestVersionStatement);
        when(countStatement.executeQuery()).thenReturn(countResultSet);
        when(countResultSet.next()).thenReturn(true);
        when(countResultSet.getObject(1)).thenReturn(0);
        when(maxStatement.executeQuery()).thenReturn(maxResultSet);
        when(maxResultSet.next()).thenReturn(true);
        when(maxResultSet.getInt(1)).thenReturn(0);
        when(maxResultSet.wasNull()).thenReturn(true);
        when(insertStatement.executeUpdate()).thenReturn(1);
        when(latestVersionStatement.executeUpdate()).thenReturn(1);
        dao.createWorkflowDef(WorkflowDef.builder().route("first").version(1).name("相同中文名称").build());
        dao.createWorkflowDef(WorkflowDef.builder().route("second").version(1).name("相同中文名称").build());
        dao.createWorkflowDef(WorkflowDef.builder().route("first").version(2).name("相同中文名称").build());
        verify(insertStatement, times(2)).setString(1, "first");
        verify(insertStatement).setString(1, "second");
        verify(insertStatement).setInt(2, 2);
        verify(connection, times(3)).commit();
    }

    private static WorkflowDef workflowDef() {
        return WorkflowDef.builder()
                .route("demo")
                .name("演示工作流")
                .version(1)
                .description("演示工作流")
                .build();
    }

    @Test
    void tagUpdateChangesOnlyJsonTagsAndDoesNotRewriteTheWholeDefinition() throws Exception {
        when(connection.prepareStatement(contains("SELECT COUNT"))).thenReturn(countStatement);
        when(countStatement.executeQuery()).thenReturn(countResultSet);
        when(countResultSet.next()).thenReturn(true);
        when(countResultSet.getObject(1)).thenReturn(1);
        when(connection.prepareStatement(contains("JSON_SET(json_data, '$.tags'"))).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);

        dao.updateWorkflowTags("HIS/query", 2, List.of(" HIS ", "门诊", "HIS", ""));

        verify(insertStatement).setString(1, "[\"HIS\",\"门诊\"]");
        verify(insertStatement).setString(2, "HIS/query");
        verify(insertStatement).setInt(3, 2);
        verify(connection, never()).prepareStatement(contains("latest_version"));
        verify(connection).commit();
    }

    @Test
    void missingVersionCannotBeCreatedThroughTagMaintenance() throws Exception {
        when(connection.prepareStatement(contains("SELECT COUNT"))).thenReturn(countStatement);
        when(countStatement.executeQuery()).thenReturn(countResultSet);
        when(countResultSet.next()).thenReturn(true);
        when(countResultSet.getObject(1)).thenReturn(0);
        assertThrows(WorkFlowConfigException.class, () -> dao.updateWorkflowTags("missing", 1, List.of()));
        verify(connection, never()).prepareStatement(startsWith("UPDATE"));
        verify(connection).rollback();
    }
}
