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
    basePackages = {"com.helianhealth.agent.mapper.hems"},
    sqlSessionTemplateRef = "hemsSqlSessionTemplate"
)
public class hemsDataSourceConfig {

    @Bean(name = "hemsDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.hems")
    public DataSource dataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "hemsSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(@Qualifier("hemsDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath*:mapper/hems/**/*.xml"));
        org.apache.ibatis.session.Configuration mybatisConfig = new org.apache.ibatis.session.Configuration();
        mybatisConfig.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(mybatisConfig);
        return bean.getObject();
    }

    @Bean(name = "hemsSqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("hemsSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "hemsTransactionManager")
    public DataSourceTransactionManager transactionManager(@Qualifier("hemsDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    /**
     * Hems 业务库 JdbcTemplate
     * 供自定义 SPI 扩展执行动态 SQL
     */
    @Bean(name = "hemsJdbcTemplate")
    public JdbcTemplate hemsJdbcTemplate(@Qualifier("hemsDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
