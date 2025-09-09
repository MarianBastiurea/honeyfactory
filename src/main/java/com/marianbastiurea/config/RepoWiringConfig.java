package com.marianbastiurea.config;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.repo.CrateRepo;
import com.marianbastiurea.domain.repo.HoneyRepo;
import com.marianbastiurea.domain.repo.JarRepo;
import com.marianbastiurea.domain.repo.LabelRepo;
import com.marianbastiurea.infrastructure.jdbc.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

@Configuration
public class RepoWiringConfig {

    @Bean
    public HoneyRepo honeyRepo(NamedParameterJdbcTemplate acaciaTpl,
                               NamedParameterJdbcTemplate rapeseedTpl,
                               NamedParameterJdbcTemplate lindenTpl,
                               NamedParameterJdbcTemplate sunflowerTpl,
                               NamedParameterJdbcTemplate wildflowerTPL,
                               NamedParameterJdbcTemplate falseindigoTPL
           ) {
        return new RouterHoneyRepo(Map.of(
                HoneyType.ACACIA, acaciaTpl,
                HoneyType.RAPESEED, rapeseedTpl,
                HoneyType.LINDEN, lindenTpl,
                HoneyType.SUNFLOWER, sunflowerTpl,
                HoneyType.WILDFLOWER, wildflowerTPL,
                HoneyType.FALSE_INDIGO, falseindigoTPL

        ));
    }

    @Bean
    public JarRepo jarRepo(NamedParameterJdbcTemplate jarsTpl) {
        return new JarRepoJdbc(jarsTpl);
    }

    @Bean
    public LabelRepo labelRepo(NamedParameterJdbcTemplate labelsTpl) {
        return new LabelRepoJdbc(labelsTpl);
    }

    @Bean
    public CrateRepo crateRepo(NamedParameterJdbcTemplate cratesTpl) {
        return new CrateRepoJdbc(cratesTpl);
    }
}
