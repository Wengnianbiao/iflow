package com.helianhealth.agent.workflow.config;

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
 * Workflow模块 - Agent数据库配置
 */
@Configuration
@MapperScan(
    basePackages = {"com.helianhealth.agent.workflow.mapper.agent"},
    sqlSessionTemplateRef = "workflowAgentSqlSessionTemplate"
)
public class WorkflowAgentDataSourceConfig {

    @Bean(name = "workflowAgentDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.agent")
    public DataSource dataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "workflowAgentSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("workflowAgentDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        // 只扫描 workflow 模块自己的 mapper XML 文件
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath:mapper/agent/*.xml"));
        // 开启驼峰映射
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
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry().register(ListIntegerTypeHandler.class);
    }
}
