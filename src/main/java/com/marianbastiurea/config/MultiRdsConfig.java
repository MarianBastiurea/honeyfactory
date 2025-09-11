package com.marianbastiurea.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MultiRdsConfig {

    @Bean(name = "acaciaDs")
    @ConfigurationProperties("rds.acacia")
    public HikariDataSource acaciaDs() { return new HikariDataSource(); }

    @Bean(name = "acaciaTpl")
    public NamedParameterJdbcTemplate acaciaTpl(@Qualifier("acaciaDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = "rapeseedDs")
    @ConfigurationProperties("rds.rapeseed")
    public HikariDataSource rapeseedDs() { return new HikariDataSource(); }

    @Bean(name = "rapeseedTpl")
    public NamedParameterJdbcTemplate rapeseedTpl(@Qualifier("rapeseedDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = "lindenDs")
    @ConfigurationProperties("rds.linden")
    public HikariDataSource lindenDs() { return new HikariDataSource(); }

    @Bean(name = "lindenTpl")
    public NamedParameterJdbcTemplate lindenTpl(@Qualifier("lindenDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = "sunflowerDs")
    @ConfigurationProperties("rds.sunflower")
    public HikariDataSource sunflowerDs() { return new HikariDataSource(); }

    @Bean(name = "sunflowerTpl")
    public NamedParameterJdbcTemplate sunflowerTpl(@Qualifier("sunflowerDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = "wildflowerDs")
    @ConfigurationProperties("rds.wildflower")
    public HikariDataSource wildflowerDs() { return new HikariDataSource(); }

    @Bean(name = "wildflowerTpl")
    public NamedParameterJdbcTemplate wildflowerTpl(@Qualifier("wildflowerDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = "falseindigoDs")
    @ConfigurationProperties("rds.falseindigo")
    public HikariDataSource falseindigoDs() { return new HikariDataSource(); }

    @Bean(name = "falseindigoTpl")
    public NamedParameterJdbcTemplate falseindigoTpl(@Qualifier("falseindigoDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = "jarsDs")
    @ConfigurationProperties("rds.jars")
    public HikariDataSource jarsDs() { return new HikariDataSource(); }

    @Bean(name = "jarsTpl")
    public NamedParameterJdbcTemplate jarsTpl(@Qualifier("jarsDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = "labelsDs")
    @ConfigurationProperties("rds.labels")
    public HikariDataSource labelsDs() { return new HikariDataSource(); }

    @Bean(name = "labelsTpl")
    public NamedParameterJdbcTemplate labelsTpl(@Qualifier("labelsDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean(name = "cratesDs")
    @ConfigurationProperties("rds.crates")
    public HikariDataSource cratesDs() { return new HikariDataSource(); }

    @Bean(name = "cratesTpl")
    public NamedParameterJdbcTemplate cratesTpl(@Qualifier("cratesDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Primary
    @Bean(name = "ordersDs")
    @ConfigurationProperties("rds.orders")
    public HikariDataSource ordersDs() { return new HikariDataSource(); }

    @Bean(name = "ordersTpl")
    public NamedParameterJdbcTemplate ordersTpl(@Qualifier("ordersDs") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}
