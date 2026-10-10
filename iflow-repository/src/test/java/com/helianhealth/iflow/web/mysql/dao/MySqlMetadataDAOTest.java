package com.helianhealth.iflow.web.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.List;

import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void getLatestWorkflowDefReadsJsonData() throws Exception {
        WorkflowDef def = workflowDef();
        String jsonData = OBJECT_MAPPER.writeValueAsString(def);
        when(connection.prepareStatement(contains("version = latest_version")))
                .thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(selectResultSet);
        when(selectResultSet.next()).thenReturn(true);
        when(selectResultSet.getObject(1)).thenReturn(jsonData);

        Optional<WorkflowDef> latest = dao.getLatestWorkflowDef("demo");

        assertTrue(latest.isPresent());
        assertEquals("demo", latest.get().getRoute());
        assertEquals(1, latest.get().getVersion());
        assertEquals("演示工作流", latest.get().getDescription());
        verify(selectStatement).setString(eq(1), eq("demo"));
        verify(connection).commit();
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

        dao.updateWorkflowDef(definition);

        verify(insertStatement).setString(1, OBJECT_MAPPER.writeValueAsString(definition));
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
