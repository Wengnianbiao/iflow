package com.helianhealth.agent.workflow.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tk.mybatis.spring.annotation.MapperScan;

import javax.sql.DataSource;

/**
 * Workflow模块 - Hems数据库配置（体软数据库）
 */
@Configuration
@MapperScan(
    basePackages = {"com.helianhealth.agent.workflow.mapper.hems"}, 
    sqlSessionTemplateRef = "workflowHemsSqlSessionTemplate"
)
public class WorkflowHemsDataSourceConfig {

    @Bean(name = "workflowHemsDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.hems")
    public DataSource dataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "workflowHemsSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("workflowHemsDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath*:mapper/hems/**/*.xml"));
        // 开启驼峰映射
        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(mybatisConfig);
        return bean.getObject();
    }

    @Bean(name = "workflowHemsSqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("workflowHemsSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "workflowHemsTransactionManager")
    public DataSourceTransactionManager transactionManager(@Qualifier("workflowHemsDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
