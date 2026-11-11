package com.helianhealth.iflow.persistence.mysql.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.dao.IndexDAO;
import com.helianhealth.iflow.core.dao.MetadataDAO;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlExecutionDAO;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlIndexDAO;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlMetadataDAO;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlWorkflowScheduleDAO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnMySqlRepository
public class MySqlRepositoryConfiguration {

    @Bean
    @ConditionalOnProperty(name = {"iflow.enabled", "iflow.scheduler.enabled"}, havingValue = "true", matchIfMissing = true)
    public MySqlWorkflowScheduleDAO mySqlWorkflowScheduleDAO(DataSource dataSource, ObjectMapper objectMapper) {
        return new MySqlWorkflowScheduleDAO(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(MetadataDAO.class)
    public MySqlMetadataDAO mySqlMetadataDAO(DataSource dataSource, ObjectMapper objectMapper) {
        return new MySqlMetadataDAO(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ExecutionDAO.class)
    public MySqlExecutionDAO mySqlExecutionDAO(
            DataSource dataSource, ObjectMapper objectMapper, MySqlIndexDAO indexDAO) {
        return new MySqlExecutionDAO(dataSource, objectMapper, indexDAO);
    }

    @Bean
    @ConditionalOnMissingBean(IndexDAO.class)
    public MySqlIndexDAO mySqlIndexDAO(DataSource dataSource, ObjectMapper objectMapper,
            ObjectProvider<SystemTaskRegistry> registryProvider) {
        return new MySqlIndexDAO(dataSource, objectMapper, registryProvider);
    }
}
