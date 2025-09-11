package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.CrateType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.repo.CrateRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CrateRepoJdbc implements CrateRepo {

    private static final Logger log = LoggerFactory.getLogger(CrateRepoJdbc.class);
    private final NamedParameterJdbcTemplate tpl;

    public CrateRepoJdbc(@Qualifier("cratesTpl") NamedParameterJdbcTemplate tpl) {
        this.tpl = tpl;
    }

    private static CrateType crateTypeFor(JarType jt) {
        return CrateType.forJarType(jt);
    }

    @Override
    public BigDecimal freeAsKg(Map<JarType, Integer> requestedJars) {
        if (requestedJars == null || requestedJars.isEmpty()) return BigDecimal.ZERO;

        List<String> crateTypes = requestedJars.keySet().stream()
                .map(jt -> crateTypeFor(jt).name())
                .toList();


        String sql = """
            SELECT crate_type, final_stock
            FROM public.crate_stock
            WHERE crate_type IN (:types)
        """;

        Map<String, Integer> availCrates = tpl.query(sql, Map.of("types", crateTypes), rs -> {
            Map<String, Integer> m = new HashMap<>();
            while (rs.next()) {
                m.put(rs.getString("crate_type"), rs.getInt("final_stock"));
            }
            return m;
        });

        BigDecimal kg = BigDecimal.ZERO;
        for (var e : requestedJars.entrySet()) {
            JarType jt = e.getKey();
            int reqJars = e.getValue() == null ? 0 : e.getValue();

            CrateType ct = crateTypeFor(jt);
            int cratesAvail = availCrates.getOrDefault(ct.name(), 0);
            int jarsSupported = ct.jarsCapacityForCrates(cratesAvail);
            int canFillJars = Math.min(reqJars, jarsSupported);

            kg = kg.add(jt.kgPerJar().multiply(BigDecimal.valueOf(canFillJars)));
        }
        log.debug("[CRATE] freeAsKg requested={}, availCrates={}, resultKg={}", requestedJars, availCrates, kg);
        return kg;
    }

    @Override
    public void reserve(Map<JarType, Integer> toReserve) {
        if (toReserve == null || toReserve.isEmpty()) return;
        for (var e : toReserve.entrySet()) {
            JarType jt = e.getKey();
            int jars = e.getValue() == null ? 0 : e.getValue();
            CrateType ct = crateTypeFor(jt);
            int deltaCrates = ct.cratesNeededForJars(jars);
            if (deltaCrates <= 0) continue;
            optimisticReserve(ct.name(), deltaCrates);
        }
    }

    @Override
    public void unreserve(Map<JarType, Integer> toUnreserve) {
        if (toUnreserve == null || toUnreserve.isEmpty()) return;
        for (var e : toUnreserve.entrySet()) {
            JarType jt = e.getKey();
            int jars = e.getValue() == null ? 0 : e.getValue();
            CrateType ct = crateTypeFor(jt);
            int deltaCrates = ct.cratesNeededForJars(jars);
            if (deltaCrates <= 0) continue;
            optimisticUnreserve(ct.name(), deltaCrates);
        }
    }

    private void optimisticReserve(String crateType, int deltaCrates) {
        final int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Long ver = tpl.queryForObject(
                    "SELECT row_version FROM public.crate_stock WHERE crate_type = :crate",
                    Map.of("crate", crateType), Long.class);

            int updated = tpl.update("""
                UPDATE public.crate_stock
                SET ordered = ordered + :delta,
                    row_version = :newver
                WHERE crate_type = :crate
                  AND row_version = :ver
                  AND (ordered + :delta) <= initial_stock
            """, Map.of("crate", crateType, "delta", deltaCrates, "ver", ver, "newver", ver + 1));

            if (updated == 1) {
                log.info("[CRATE] reserve crateType={} +{} OK (ver {}→{})", crateType, deltaCrates, ver, ver + 1);
                return;
            }
            log.warn("[CRATE] reserve conflict crateType={} (attempt {}/{}) — retrying...", crateType, attempt, maxRetries);
        }
        throw new IllegalStateException("Optimistic lock failed reserving crates for " + crateType);
    }

    private void optimisticUnreserve(String crateType, int deltaCrates) {
        final int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Long ver = tpl.queryForObject(
                    "SELECT row_version FROM public.crate_stock WHERE crate_type = :crate",
                    Map.of("crate", crateType), Long.class);

            int updated = tpl.update("""
                UPDATE public.crate_stock
                SET ordered = GREATEST(ordered - :delta, 0),
                    row_version = :newver
                WHERE crate_type = :crate
                  AND row_version = :ver
            """, Map.of("crate", crateType, "delta", deltaCrates, "ver", ver, "newver", ver + 1));

            if (updated == 1) {
                log.info("[CRATE] unreserve crateType={} -{} OK (ver {}→{})", crateType, deltaCrates, ver, ver + 1);
                return;
            }
            log.warn("[CRATE] unreserve conflict crateType={} (attempt {}/{}) — retrying...", crateType, attempt, maxRetries);
        }
        throw new IllegalStateException("Optimistic lock failed unreserving crates for " + crateType);
    }
}
