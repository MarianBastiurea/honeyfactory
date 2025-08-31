package com.marianbastiurea.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MultiRdsConfig {

    @Bean
    @ConfigurationProperties("rds.acacia")
    public HikariDataSource acaciaDs() { return new HikariDataSource(); }

    @Bean("acaciaTpl")
    public NamedParameterJdbcTemplate acaciaTpl(DataSource acaciaDs) {
        return new NamedParameterJdbcTemplate(acaciaDs);
    }

    @Bean
    @ConfigurationProperties("rds.rapeseed")
    public HikariDataSource rapeseedDs() { return new HikariDataSource(); }

    @Bean("rapeseedTpl")
    public NamedParameterJdbcTemplate rapeseedTpl(DataSource rapeseedDs) {
        return new NamedParameterJdbcTemplate(rapeseedDs);
    }

    @Bean
    @ConfigurationProperties("rds.linden")
    public HikariDataSource lindenDs() { return new HikariDataSource(); }

    @Bean("lindenTpl")
    public NamedParameterJdbcTemplate lindenTpl(DataSource lindenDs) {
        return new NamedParameterJdbcTemplate(lindenDs);
    }

    @Bean
    @ConfigurationProperties("rds.sunflower")
    public HikariDataSource sunflowerDs() { return new HikariDataSource(); }

    @Bean("sunflowerTpl")
    public NamedParameterJdbcTemplate sunflowerTpl(DataSource sunflowerDs) {
        return new NamedParameterJdbcTemplate(sunflowerDs);
    }

    @Bean
    @ConfigurationProperties("rds.wildflower")
    public HikariDataSource wildlowerDs() { return new HikariDataSource(); }

    @Bean("wildflowerTpl")
    public NamedParameterJdbcTemplate wildflowerTpl(DataSource wildflowerDs) {
        return new NamedParameterJdbcTemplate(wildflowerDs);
    }
    @Bean
    @ConfigurationProperties("rds.falseindigo")
    public HikariDataSource falseindigoDs() { return new HikariDataSource(); }

    @Bean("falseindigoTpl")
    public NamedParameterJdbcTemplate falseindigoTpl(DataSource sunflowerDs) {
        return new NamedParameterJdbcTemplate(sunflowerDs);
    }


    @Bean
    @ConfigurationProperties("rds.jars")
    public HikariDataSource jarsDs() { return new HikariDataSource(); }

    @Bean("jarsTpl")
    public NamedParameterJdbcTemplate jarsTpl(DataSource jarsDs) {
        return new NamedParameterJdbcTemplate(jarsDs);
    }


    @Bean
    @ConfigurationProperties("rds.labels")
    public HikariDataSource labelsDs() { return new HikariDataSource(); }

    @Bean("labelsTpl")
    public NamedParameterJdbcTemplate labelsTpl(DataSource labelsDs) {
        return new NamedParameterJdbcTemplate(labelsDs);
    }


    @Bean
    @ConfigurationProperties("rds.crates")
    public HikariDataSource cratesDs() { return new HikariDataSource(); }

    @Bean("cratesTpl")
    public NamedParameterJdbcTemplate cratesTpl(DataSource cratesDs) {
        return new NamedParameterJdbcTemplate(cratesDs);
    }

}
