package com.marianbastiurea.config;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.model.OrderRecord;
import com.marianbastiurea.domain.repository.OrderRecordRepository;
import com.marianbastiurea.domain.services.ReservationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class StartupRunnerConfig {

    private static final Logger log = LoggerFactory.getLogger(StartupRunnerConfig.class);


    private static BigDecimal jarsToKg(Map<JarType, Integer> m) {
        if (m == null || m.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (var e : m.entrySet()) {
            if (e.getKey() == null) continue;
            int q = e.getValue() == null ? 0 : e.getValue();
            if (q <= 0) continue;
            sum = sum.add(e.getKey().kgPerJar().multiply(BigDecimal.valueOf(q)));
        }
        return sum.max(BigDecimal.ZERO);
    }

    private static String fmtJarBreakdown(Map<JarType, Integer> m) {
        if (m == null || m.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        int total = 0;
        BigDecimal totalKg = BigDecimal.ZERO;
        for (JarType jt : JarType.values()) {
            int q = m.getOrDefault(jt, 0);
            if (q <= 0) continue;
            BigDecimal kg = jt.kgPerJar().multiply(BigDecimal.valueOf(q));
            sb.append(String.format("  - %-6s : qty=%-5d  kgPerJar=%-4s  kg=%s%n",
                    jt.name(), q, jt.kgPerJar().toPlainString(), kg.toPlainString()));
            total += q;
            totalKg = totalKg.add(kg);
        }
        sb.append(String.format("  Σ jars=%d  Σ kg=%s", total, totalKg.toPlainString()));
        return sb.toString();
    }

    @Bean
    @ConditionalOnProperty(name = "app.process-orders-on-startup", havingValue = "true", matchIfMissing = false)
    CommandLineRunner runOnce(ReservationOrchestrator orchestrator,
                              @Qualifier("ordersTpl") NamedParameterJdbcTemplate ordersTpl,
                              OrderRecordRepository orderRecords) {
        return args -> {
            log.info("Startup runner enabled: app.process-orders-on-startup=true");

            final String sql = """
                        SELECT order_number, honey_type, jar_type, quantity
                          FROM public.orders
                    """;
            long t0 = System.nanoTime();
            List<Map<String, Object>> rows = ordersTpl.getJdbcTemplate().queryForList(sql);
            long tFetch = System.nanoTime();

            if (rows == null || rows.isEmpty()) {
                return;
            }

            Map<Integer, Map<HoneyType, Map<JarType, Integer>>> grouped = new LinkedHashMap<>();
            int skipped = 0;

            for (Map<String, Object> r : rows) {
                try {
                    Integer ord = ((Number) r.get("order_number")).intValue();
                    HoneyType honey = HoneyType.valueOf(((String) r.get("honey_type")).trim());
                    JarType jar = JarType.valueOf(((String) r.get("jar_type")).trim());
                    int qty = ((Number) r.get("quantity")).intValue();

                    if (qty <= 0) {
                        skipped++;
                        continue;
                    }

                    grouped
                            .computeIfAbsent(ord, k -> new EnumMap<>(HoneyType.class))
                            .computeIfAbsent(honey, k -> new EnumMap<>(JarType.class))
                            .merge(jar, qty, Integer::sum);
                } catch (Exception ex) {
                    skipped++;
                }
            }
            long tGroup = System.nanoTime();
            log.info("Grouping complete in {} ms. Skipped {} row(s).",
                    (tGroup - tFetch) / 1_000_000, skipped);

            grouped.forEach((orderNo, perHoney) ->
                    perHoney.forEach((honey, jarsMap) -> {
                        int totalJars = jarsMap.values().stream().mapToInt(Integer::intValue).sum();
                        BigDecimal totalKg = jarsToKg(jarsMap);
                        log.info("[orders/agg] order#{} [{}] -> totalJars={} totalKg={}",
                                orderNo, honey, totalJars, totalKg);
                        log.info("[orders/agg] order#{} [{}] breakdown:\n{}",
                                orderNo, honey, fmtJarBreakdown(jarsMap));
                    })
            );

            int success = 0, failed = 0;

            for (var ordEntry : grouped.entrySet()) {
                Integer orderNumber = ordEntry.getKey();

                for (var honeyEntry : ordEntry.getValue().entrySet()) {
                    HoneyType honey = honeyEntry.getKey();
                    Map<JarType, Integer> jarQuantities = honeyEntry.getValue();
                    Order order = new Order(honey, jarQuantities, orderNumber);

                    long tOrch0 = System.nanoTime();
                    try {
                        var result = orchestrator.reserveFor(order);
                        long tOrch1 = System.nanoTime();

                        OrderRecord.Status status = result.success()
                                ? OrderRecord.Status.RESERVED
                                : OrderRecord.Status.FAILED;

                        orderRecords.save(new OrderRecord(
                                null,
                                orderNumber,
                                honey,
                                jarQuantities,
                                Instant.now(),
                                status,
                                result.message()
                        ));

                        if (result.success()) {
                            success++;
                        } else {
                            failed++;
                        }
                    } catch (Exception ex) {
                        failed++;
                        try {
                            orderRecords.save(new OrderRecord(
                                    null,
                                    orderNumber,
                                    honey,
                                    jarQuantities,
                                    Instant.now(),
                                    OrderRecord.Status.FAILED,
                                    "EXCEPTION: " + ex.getMessage()
                            ));
                        } catch (Exception ignore) {
                        }
                    }
                }
            }
            long tEnd = System.nanoTime();
            log.info("Startup runner finished in {} ms. Success={}, Failed={}.",
                    (tEnd - t0) / 1_000_000, success, failed);
        };
    }
}
