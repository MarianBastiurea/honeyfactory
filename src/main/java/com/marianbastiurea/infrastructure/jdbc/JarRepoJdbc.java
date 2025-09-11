package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.repo.JarRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Repository
public class JarRepoJdbc implements JarRepo {

    private static final Logger log = LoggerFactory.getLogger(JarRepoJdbc.class);
    private final NamedParameterJdbcTemplate tpl;

    public JarRepoJdbc(@Qualifier("jarsTpl") NamedParameterJdbcTemplate tpl) {
        this.tpl = tpl;
    }

    @Override
    public BigDecimal freeAsKg(Map<JarType, Integer> requestedJars, HoneyType honeyType) {
        if (requestedJars == null || requestedJars.isEmpty()) return BigDecimal.ZERO;

        List<String> types = requestedJars.keySet().stream().map(Enum::name).toList();

        String sql = """
                SELECT jar_type, final_stock
                FROM public.jar_stock
                WHERE jar_type IN (:types)
                """;

        Map<JarType, Integer> available = tpl.query(sql, Map.of("types", types), rs -> {
            Map<JarType, Integer> m = new EnumMap<>(JarType.class);
            while (rs.next()) {
                JarType type = JarType.valueOf(rs.getString("jar_type"));
                m.put(type, rs.getInt("final_stock"));
            }
            return m;
        });

        BigDecimal kg = BigDecimal.ZERO;
        for (var e : requestedJars.entrySet()) {
            JarType type = e.getKey();
            int req = e.getValue() == null ? 0 : e.getValue();
            int avail = available.getOrDefault(type, 0);
            int canFill = Math.min(req, avail);
            kg = kg.add(type.kgPerJar().multiply(BigDecimal.valueOf(canFill)));
        }
        log.debug("[JAR] freeAsKg requested={}, available={}, resultKg={}", requestedJars, available, kg);
        return kg;
    }

    @Override
    public void reserve(Map<JarType, Integer> toReserve) {
        if (toReserve == null || toReserve.isEmpty()) return;

        String sql = """
                  UPDATE public.jar_stock
                  SET ordered = ordered + :delta,
                      row_version = row_version + 1
                  WHERE jar_type = :jar
                    AND (ordered + :delta) <= initial_stock
                """;

        @SuppressWarnings("unchecked")
        Map<String, Object>[] batch = toReserve.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "jar", e.getKey().name(),
                        "delta", e.getValue() == null ? 0 : e.getValue()))
                .toArray(Map[]::new);

        int[] rows = tpl.batchUpdate(sql, batch);
        int updated = java.util.Arrays.stream(rows).sum();
        log.info("[JAR] reserve {} -> rowsUpdated={}", toReserve, updated);
    }

    @Override
    public void unreserve(Map<JarType, Integer> toUnreserve) {
        if (toUnreserve == null || toUnreserve.isEmpty()) return;

        String sql = """
                  UPDATE public.jar_stock
                  SET ordered = GREATEST(ordered - :delta, 0),
                      row_version = row_version + 1
                  WHERE jar_type = :jar
                """;
        @SuppressWarnings("unchecked")
        Map<String, Object>[] batch = toUnreserve.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "jar", e.getKey().name(),
                        "delta", e.getValue() == null ? 0 : e.getValue()))
                .toArray(Map[]::new);

        int[] rows = tpl.batchUpdate(sql, batch);
        int updated = java.util.Arrays.stream(rows).sum();
        log.info("[JAR] unreserve {} -> rowsUpdated={}", toUnreserve, updated);
    }
}
