package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.repo.OrderRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class OrderRepoJdbc implements OrderRepo {

    private static final Logger log = LoggerFactory.getLogger(OrderRepoJdbc.class);
    private final NamedParameterJdbcTemplate tpl;

    public OrderRepoJdbc(@Qualifier("ordersTpl") NamedParameterJdbcTemplate tpl) {
        this.tpl = tpl;
    }

    @Override
    public List<Order> findByOrderNumber(Integer orderNumber) {
        List<Map<String, Object>> rows = tpl.queryForList("""
            SELECT honey_type, jar_type, quantity
              FROM orders
             WHERE order_number = :ord
             ORDER BY honey_type, jar_type
            """, Map.of("ord", orderNumber));

        LinkedHashMap<HoneyType, EnumMap<JarType, Integer>> grouped = new LinkedHashMap<>();
        for (var r : rows) {
            HoneyType honey = HoneyType.valueOf(r.get("honey_type").toString().trim().toUpperCase(Locale.ROOT));
            JarType   jar   = JarType.valueOf(r.get("jar_type").toString().trim().toUpperCase(Locale.ROOT));
            int       qty   = ((Number) r.get("quantity")).intValue();
            grouped.computeIfAbsent(honey, k -> new EnumMap<>(JarType.class))
                    .merge(jar, qty, Integer::sum);
        }

        List<Order> out = new ArrayList<>(grouped.size());
        grouped.forEach((honey, jars) ->
                out.add(new Order(honey, Collections.unmodifiableMap(jars), orderNumber))
        );
        log.info("[findByOrderNumber] order#{} -> {} tip(uri) de miere", orderNumber, out.size());
        return Collections.unmodifiableList(out);
    }

    @Override
    public void logProcessingBatch(int orderNumber, List<ProcessingLogRow> lines) {
        if (lines == null || lines.isEmpty()) return;

        final String sql = """
            INSERT INTO processing_log(order_number, honey_type, jar_type, requested_qty, delivered_qty, reason)
            VALUES (:o, :h, :j, :rq, :dq, :r)
            """;

        MapSqlParameterSource[] batch = lines.stream()
                .map(l -> new MapSqlParameterSource()
                        .addValue("o", orderNumber)
                        .addValue("h", l.honeyType().name())
                        .addValue("j", l.jarType().name())
                        .addValue("rq", l.requestedQty())
                        .addValue("dq", l.deliveredQty())
                        .addValue("r", l.reason() == null ? "" : l.reason()))
                .toArray(MapSqlParameterSource[]::new);

        tpl.batchUpdate(sql, batch);
        log.info("[logProcessingBatch] order#{} -> {} rând(uri) inserate în processing_log", orderNumber, lines.size());
    }


    @Override
    public void insertProcessingLog(int orderNumber, JarType jt, int req, int del, String reason) {
        List<String> types = tpl.queryForList("""
            SELECT DISTINCT honey_type
              FROM orders
             WHERE order_number = :o AND jar_type = :j
            """, Map.of("o", orderNumber, "j", jt.name()), String.class);

        String honeyType = types.get(0);
        tpl.update("""
            INSERT INTO processing_log(order_number, honey_type, jar_type, requested_qty, delivered_qty, reason)
            VALUES (:o, :h, :j, :rq, :dq, :r)
            """,
                new MapSqlParameterSource()
                        .addValue("o", orderNumber)
                        .addValue("h", honeyType)
                        .addValue("j", jt.name())
                        .addValue("rq", req)
                        .addValue("dq", del)
                        .addValue("r", reason == null ? "" : reason)
        );
        log.info("[insertProcessingLog] order#{} -> 1 rând inserat (ht={} jt={} rq={} dq={})",
                orderNumber, honeyType, jt, req, del);
    }
}
