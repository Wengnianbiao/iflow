package com.helianhealth.agent.repository.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.helianhealth.agent.common.typehandler.ListIntegerTypeHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tk.mybatis.mapper.autoconfigure.ConfigurationCustomizer;
import tk.mybatis.spring.annotation.MapperScan;

import javax.sql.DataSource;

/**
 * Repository模块 - Agent数据库配置
 * 提供工作流、节点、参数配置等核心领域模型的数据访问
 */
@Configuration
@MapperScan(
    basePackages = {"com.helianhealth.agent.repository.mapper"},
    sqlSessionTemplateRef = "workflowAgentSqlSessionTemplate"
)
public class RepositoryAgentDataSourceConfig {

    @Bean(name = "workflowAgentDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.agent")
    public DataSource dataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "workflowAgentSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("workflowAgentDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath:mapper/agent/*.xml"));
        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(mybatisConfig);
        return bean.getObject();
    }

    @Bean(name = "workflowAgentSqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("workflowAgentSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "workflowAgentTransactionManager")
    public DataSourceTransactionManager transactionManager(@Qualifier("workflowAgentDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean
    public ConfigurationCustomizer repositoryMybatisConfigurationCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry().register(ListIntegerTypeHandler.class);
    }
}
