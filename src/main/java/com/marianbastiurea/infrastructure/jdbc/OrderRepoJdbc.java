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

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class OrderRepoJdbc implements OrderRepo {

    private static final Logger log = LoggerFactory.getLogger(OrderRepoJdbc.class);
    private final NamedParameterJdbcTemplate tpl;

    public OrderRepoJdbc(@Qualifier("ordersTpl") NamedParameterJdbcTemplate tpl) {
        this.tpl = tpl;
    }

    @Override
    public void upsertOrderLines(Integer orderNumber, HoneyType honeyType, Map<JarType, Integer> jarQuantities) {
        final String sql = """
            INSERT INTO orders(order_number, honey_type, jar_type, quantity, status, processed_at, failed_reason)
            VALUES (:ord, :honey, :jar, :qty, 'PENDING', NULL, NULL)
            ON CONFLICT (order_number, honey_type, jar_type) DO UPDATE
            SET quantity = EXCLUDED.quantity,
                status = 'PENDING',
                processed_at = NULL,
                failed_reason = NULL
            """;

        var batch = jarQuantities.entrySet().stream()
                .map(e -> new MapSqlParameterSource()
                        .addValue("ord", orderNumber)
                        .addValue("honey", honeyType.name())
                        .addValue("jar", e.getKey().name())
                        .addValue("qty", e.getValue()))
                .toArray(MapSqlParameterSource[]::new);

        tpl.batchUpdate(sql, batch);
    }

    @Override
    public Optional<Order> findByOrderNumber(Integer orderNumber) {
        // considerăm că un orderNumber are un singur honey_type; altfel -> Optional.empty()
        List<String> types = tpl.queryForList(
                "SELECT DISTINCT honey_type FROM orders WHERE order_number = :ord",
                Map.of("ord", orderNumber), String.class);
        if (types.isEmpty() || types.size() > 1) return Optional.empty();

        HoneyType honey = HoneyType.valueOf(types.get(0));
        return findByOrderNumberAndHoneyType(orderNumber, honey);
    }

    @Override
    public Optional<Order> findByOrderNumberAndHoneyType(Integer orderNumber, HoneyType honeyType) {
        var rows = tpl.queryForList("""
                SELECT jar_type, quantity
                  FROM orders
                 WHERE order_number = :ord AND honey_type = :honey
                 ORDER BY jar_type
                """, Map.of("ord", orderNumber, "honey", honeyType.name()));
        if (rows.isEmpty()) return Optional.empty();

        Map<JarType, Integer> jars = new LinkedHashMap<>();
        for (var r : rows) {
            jars.put(JarType.valueOf(r.get("jar_type").toString()),
                    ((Number) r.get("quantity")).intValue());
        }
        return Optional.of(new Order(honeyType, jars, orderNumber));
    }

    @Override
    public List<Order> findByHoneyType(HoneyType honeyType) {
        var rows = tpl.queryForList("""
                SELECT order_number, jar_type, quantity
                  FROM orders
                 WHERE honey_type = :honey
                 ORDER BY order_number, jar_type
                """, Map.of("honey", honeyType.name()));

        if (rows.isEmpty()) return List.of();

        Map<Integer, Map<JarType, Integer>> byOrder = new LinkedHashMap<>();
        for (var r : rows) {
            Integer ord = ((Number) r.get("order_number")).intValue();
            JarType jar = JarType.valueOf(r.get("jar_type").toString());
            Integer qty = ((Number) r.get("quantity")).intValue();
            byOrder.computeIfAbsent(ord, k -> new LinkedHashMap<>()).put(jar, qty);
        }

        List<Order> result = new ArrayList<>();
        for (var e : byOrder.entrySet()) {
            result.add(new Order(honeyType, e.getValue(), e.getKey()));
        }
        return result;
    }

    @Override
    public List<Order> loadPendingGrouped() {
        final String sql = """
            SELECT order_number, honey_type, jar_type, SUM(quantity) AS qty
              FROM orders
             WHERE status = :status
             GROUP BY order_number, honey_type, jar_type
             ORDER BY order_number, honey_type
            """;
        List<Map<String, Object>> rows = tpl.queryForList(sql, Map.of("status", "PENDING"));

        record Key(int ord, HoneyType honey) {}
        LinkedHashMap<Key, EnumMap<JarType, Integer>> agg = new LinkedHashMap<>();

        for (var r : rows) {
            int ord = ((Number) r.get("order_number")).intValue();
            HoneyType honey = HoneyType.valueOf(r.get("honey_type").toString().trim().toUpperCase(Locale.ROOT));
            JarType jar = JarType.valueOf(r.get("jar_type").toString().trim().toUpperCase(Locale.ROOT));
            int qty = ((Number) r.get("qty")).intValue();

            var key = new Key(ord, honey);
            agg.computeIfAbsent(key, k -> new EnumMap<>(JarType.class))
                    .merge(jar, qty, Integer::sum);
        }

        List<Order> out = agg.entrySet().stream()
                .map(e -> new Order(e.getKey().honey(), Collections.unmodifiableMap(e.getValue()), e.getKey().ord()))
                .collect(Collectors.toList());

        log.info("[loadPendingGrouped] Grupate {} (order,honey).", out.size());
        return out;
    }

    @Override
    public boolean tryMarkProcessing(int orderNumber, HoneyType type) {
        // locking/idempotency simplu: unică pe (order_number,honey_type)
        final String sql = """
            INSERT INTO processed_orders(order_number, honey_type)
            VALUES (:o, :t)
            ON CONFLICT DO NOTHING
            """;
        int updated = tpl.update(sql, Map.of("o", orderNumber, "t", type.name()));
        if (updated == 1) {
            tpl.update("""
                UPDATE orders
                   SET status = 'PROCESSING'
                 WHERE order_number = :o AND honey_type = :t
                """, Map.of("o", orderNumber, "t", type.name()));
            return true;
        }
        return false;
    }

    @Override
    public void markCompleted(int orderNumber, HoneyType type, BigDecimal deliveredKg) {
        tpl.update("""
            UPDATE orders
               SET status = 'DELIVERED',
                   processed_at = NOW()
             WHERE order_number = :o AND honey_type = :t
            """, Map.of("o", orderNumber, "t", type.name()));

        tpl.update("""
            INSERT INTO processed_orders(order_number, honey_type)
            VALUES (:o, :t)
            ON CONFLICT DO NOTHING
            """, Map.of("o", orderNumber, "t", type.name()));
    }

    @Override
    public void markFailed(int orderNumber, HoneyType type, String reason) {
        tpl.update("""
            UPDATE orders
               SET status = 'FAILED',
                   processed_at = NOW(),
                   failed_reason = :r
             WHERE order_number = :o AND honey_type = :t
            """, Map.of("o", orderNumber, "t", type.name(), "r", reason));

        tpl.update("""
            INSERT INTO processed_orders(order_number, honey_type)
            VALUES (:o, :t)
            ON CONFLICT DO NOTHING
            """, Map.of("o", orderNumber, "t", type.name()));
    }

    @Override
    public void deleteByOrderNumber(Integer orderNumber) {
        tpl.update("DELETE FROM orders WHERE order_number = :ord", Map.of("ord", orderNumber));
        tpl.update("DELETE FROM processed_orders WHERE order_number = :ord", Map.of("ord", orderNumber));
    }
}
