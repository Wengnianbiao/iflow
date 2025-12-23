package com.helianhealth.agent.log.config;

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
 * Log模块 - Agent数据库配置
 */
@Configuration
@MapperScan(
    basePackages = {"com.helianhealth.agent.log.mapper"}, 
    sqlSessionTemplateRef = "logAgentSqlSessionTemplate"
)
public class LogDataSourceConfig {

    @Bean(name = "logAgentDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.agent")
    public DataSource dataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "logAgentSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("logAgentDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath*:mapper/agent/InterfaceInvokeLogMapper.xml"));
        // 开启驼峰映射
        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(mybatisConfig);
        return bean.getObject();
    }

    @Bean(name = "logAgentSqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("logAgentSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "logAgentTransactionManager")
    public DataSourceTransactionManager transactionManager(@Qualifier("logAgentDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
