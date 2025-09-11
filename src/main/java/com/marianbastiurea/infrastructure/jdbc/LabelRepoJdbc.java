package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.enums.LabelType;
import com.marianbastiurea.domain.repo.LabelRepo;
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
public class LabelRepoJdbc implements LabelRepo {

    private static final Logger log = LoggerFactory.getLogger(LabelRepoJdbc.class);
    private final NamedParameterJdbcTemplate tpl;

    public LabelRepoJdbc(@Qualifier("labelsTpl") NamedParameterJdbcTemplate tpl) {
        this.tpl = tpl;
    }

    private static LabelType labelTypeFor(JarType jt) {
        return switch (jt) {
            case JAR200 -> LabelType.LABEL200;
            case JAR400 -> LabelType.LABEL400;
            case JAR800 -> LabelType.LABEL800;
        };
    }

    @Override
    public BigDecimal freeAsKg(Map<JarType, Integer> requestedJars) {
        if (requestedJars == null || requestedJars.isEmpty()) return BigDecimal.ZERO;

        List<String> labelTypes = requestedJars.keySet().stream()
                .map(jt -> labelTypeFor(jt).name())
                .toList();

        String sql = """
            SELECT label_type, final_stock
            FROM public.label_stock
            WHERE label_type IN (:types)
            """;

        Map<LabelType, Integer> available = tpl.query(sql, Map.of("types", labelTypes), rs -> {
            Map<LabelType, Integer> m = new EnumMap<>(LabelType.class);
            while (rs.next()) {
                LabelType lt = LabelType.valueOf(rs.getString("label_type"));
                m.put(lt, rs.getInt("final_stock"));
            }
            return m;
        });

        BigDecimal kg = BigDecimal.ZERO;
        for (var e : requestedJars.entrySet()) {
            JarType jt = e.getKey();
            int req = e.getValue() == null ? 0 : e.getValue();
            int avail = available.getOrDefault(labelTypeFor(jt), 0); // 1 etichetă = 1 borcan
            int canFill = Math.min(req, avail);
            kg = kg.add(jt.kgPerJar().multiply(BigDecimal.valueOf(canFill)));
        }
        log.debug("[LABEL] freeAsKg requested={}, available={}, resultKg={}", requestedJars, available, kg);
        return kg;
    }

    @Override
    public void reserve(Map<JarType, Integer> toReserve) {
        if (toReserve == null || toReserve.isEmpty()) return;

        String sql = """
            UPDATE public.label_stock
            SET ordered = ordered + :delta
            WHERE label_type = :label
              AND (ordered + :delta) <= initial_stock
            """;

        @SuppressWarnings("unchecked")
        Map<String, Object>[] batch = toReserve.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "label", labelTypeFor(e.getKey()).name(),
                        "delta", e.getValue() == null ? 0 : e.getValue()))
                .toArray(Map[]::new);

        int[] rows = tpl.batchUpdate(sql, batch);
        int updated = java.util.Arrays.stream(rows).sum();
        log.info("[LABEL] reserve {} -> rowsUpdated={}", toReserve, updated);
    }

    @Override
    public void unreserve(Map<JarType, Integer> toUnreserve) {
        if (toUnreserve == null || toUnreserve.isEmpty()) return;

        String sql = """
            UPDATE public.label_stock
            SET ordered = GREATEST(ordered - :delta, 0)
            WHERE label_type = :label
            """;

        @SuppressWarnings("unchecked")
        Map<String, Object>[] batch = toUnreserve.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "label", labelTypeFor(e.getKey()).name(),
                        "delta", e.getValue() == null ? 0 : e.getValue()))
                .toArray(Map[]::new);

        int[] rows = tpl.batchUpdate(sql, batch);
        int updated = java.util.Arrays.stream(rows).sum();
        log.info("[LABEL] unreserve {} -> rowsUpdated={}", toUnreserve, updated);
    }
}
