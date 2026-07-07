package com.helianhealth.agent.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
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
        assertEquals("demo", latest.get().getName());
        assertEquals(1, latest.get().getVersion());
        assertEquals("演示工作流", latest.get().getDescription());
        verify(selectStatement).setString(eq(1), eq("demo"));
        verify(connection).commit();
    }

    private static WorkflowDef workflowDef() {
        return WorkflowDef.builder()
                .name("demo")
                .version(1)
                .description("演示工作流")
                .build();
    }
}
