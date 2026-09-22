package com.helianhealth.iflow.web.mysql.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnMySqlRepository
@Import(DataSourceAutoConfiguration.class)
public class MySqlRepositoryConfiguration {
}
