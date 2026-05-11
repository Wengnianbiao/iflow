package com.helianhealth.agent.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tk.mybatis.spring.annotation.MapperScan;

import javax.sql.DataSource;

@Configuration
@MapperScan(
    basePackages = {"com.helianhealth.agent.repository.mapper.hems"},
    sqlSessionTemplateRef = "repositoryHemsSqlSessionTemplate"
)
public class RepositoryHemsDataSourceConfig {

    @Bean(name = "repositoryHemsDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.hems")
    public DataSource dataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "repositoryHemsSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("repositoryHemsDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath*:mapper/hems/**/*.xml"));
        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(mybatisConfig);
        return bean.getObject();
    }

    @Bean(name = "repositoryHemsSqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("repositoryHemsSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "repositoryHemsTransactionManager")
    public DataSourceTransactionManager transactionManager(@Qualifier("repositoryHemsDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    /**
     * Hems 业务库 JdbcTemplate
     * 供自定义 SPI 扩展执行动态 SQL
     */
    @Bean(name = "hemsJdbcTemplate")
    public JdbcTemplate hemsJdbcTemplate(@Qualifier("repositoryHemsDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
