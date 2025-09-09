package com.marianbastiurea.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.TreeMap;

@Configuration
public class StartupSmoke {

    private static final Logger log = LoggerFactory.getLogger(StartupSmoke.class);

    @Bean
    @ConditionalOnProperty(prefix = "smoke.db", name = "enabled", havingValue = "true", matchIfMissing = false)
    ApplicationRunner pingAllDbs(@Value("${app.run-on-startup:true}") boolean run,
                                 @Value("${smoke.db.fail-fast:false}") boolean failFast,
                                 @Value("${smoke.db.query-timeout-seconds:5}") int timeoutSeconds,
                                 Map<String, NamedParameterJdbcTemplate> tpls) {
        return args -> {
            if (!run) {
                log.info("Startup smoke runner is disabled via app.run-on-startup=false.");
                return;
            }

            if (tpls == null || tpls.isEmpty()) {
                log.warn("No NamedParameterJdbcTemplate beans found. Nothing to ping.");
                return;
            }

            log.info("Starting DB smoke pings (templates={}, timeout={}s, failFast={}).",
                    tpls.size(), timeoutSeconds, failFast);
            if (log.isDebugEnabled()) {
                log.debug("Templates to ping: {}", new TreeMap<>(tpls).keySet());
            }

            long t0 = System.nanoTime();
            int ok = 0, failed = 0;

            // sort by bean name for deterministic logging
            for (var entry : new TreeMap<>(tpls).entrySet()) {
                String name = entry.getKey();
                NamedParameterJdbcTemplate tpl = entry.getValue();
                JdbcTemplate jt = tpl.getJdbcTemplate();

                long start = System.nanoTime();
                try {
                    Integer one = jt.execute((ConnectionCallback<Integer>) con -> {
                        try (PreparedStatement ps = configure(con, timeoutSeconds);
                             ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? rs.getInt(1) : null;
                        }
                    });

                    long tookMs = (System.nanoTime() - start) / 1_000_000;
                    if (one != null && one == 1) {
                        ok++;
                        log.info("DB ping OK   [{}] in {} ms.", name, tookMs);
                    } else {
                        failed++;
                        log.warn("DB ping ODD  [{}]: result={}, in {} ms.", name, one, tookMs);
                        if (failFast) throw new IllegalStateException("Unexpected ping result: " + one);
                    }
                } catch (Exception ex) {
                    failed++;
                    long tookMs = (System.nanoTime() - start) / 1_000_000;
                    log.error("DB ping FAILED [{}] in {} ms: {}", name, tookMs, ex.getMessage(), ex);
                    if (failFast) {
                        long totalMs = (System.nanoTime() - t0) / 1_000_000;
                        log.error("Fail-fast enabled — aborting startup. Pings so far: ok={}, failed={}, elapsed={} ms.",
                                ok, failed, totalMs);
                        throw new IllegalStateException("DB ping failed for [" + name + "]", ex);
                    }
                }
            }

            long totalMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("DB smoke pings finished: total={}, ok={}, failed={}, elapsed={} ms.",
                    tpls.size(), ok, failed, totalMs);
        };
    }

    private static PreparedStatement configure(Connection con, int timeoutSeconds) throws SQLException {
        PreparedStatement ps = con.prepareStatement("SELECT 1");
        try {
            ps.setQueryTimeout(timeoutSeconds);
        } catch (Exception ignored) {
            // unii drivere ignoră/nu suportă timeout-ul per statement
        }
        return ps;
    }
}
