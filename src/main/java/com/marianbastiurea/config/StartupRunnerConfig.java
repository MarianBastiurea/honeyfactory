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

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class StartupRunnerConfig {

    private static final Logger log = LoggerFactory.getLogger(StartupRunnerConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.process-orders-on-startup", havingValue = "true", matchIfMissing = false)
    CommandLineRunner runOnce(ReservationOrchestrator orchestrator,
                              @Qualifier("ordersTpl") NamedParameterJdbcTemplate ordersTpl,
                              OrderRecordRepository orderRecords) {
        return args -> {
            log.info("Startup runner: processing orders from RDS is ENABLED (app.process-orders-on-startup=true).");

            final String sql = """
                SELECT order_number, honey_type, jar_type, quantity
                FROM public.orders
            """;
            log.debug("Executing SQL to fetch orders:\n{}", sql);

            long t0 = System.nanoTime();
            List<Map<String, Object>> rows = ordersTpl.getJdbcTemplate().queryForList(sql);
            long tFetch = System.nanoTime();

            if (rows.isEmpty()) {
                log.warn("No rows found in public.orders. Nothing to process.");
                return;
            }
            log.info("Fetched {} order line(s) from public.orders in {} ms.",
                    rows.size(), (tFetch - t0) / 1_000_000);

            Map<Integer, Map<HoneyType, Map<JarType, Integer>>> grouped = new LinkedHashMap<>();

            int skipped = 0;
            for (Map<String, Object> r : rows) {
                try {
                    Integer ord = ((Number) r.get("order_number")).intValue();
                    HoneyType honey = HoneyType.valueOf(((String) r.get("honey_type")).trim());
                    JarType jar = JarType.valueOf(((String) r.get("jar_type")).trim());
                    Integer qty = ((Number) r.get("quantity")).intValue();

                    grouped
                            .computeIfAbsent(ord, k -> new EnumMap<>(HoneyType.class))
                            .computeIfAbsent(honey, k -> new EnumMap<>(JarType.class))
                            .merge(jar, qty, Integer::sum);

                    log.trace("Grouped row -> order={}, honey={}, jar={}, qty={}", ord, honey, jar, qty);
                } catch (Exception ex) {
                    skipped++;
                    log.warn("Skipping invalid row {} due to parsing error: {}", r, ex.toString());
                }
            }

            int combos = grouped.values().stream().mapToInt(m -> m.size()).sum();
            log.info("Grouping complete: {} order(s), {} (order+honey) combo(s). Skipped {} row(s).",
                    grouped.size(), combos, skipped);

            int success = 0;
            int failed = 0;

            for (var ordEntry : grouped.entrySet()) {
                Integer orderNumber = ordEntry.getKey();
                for (var honeyEntry : ordEntry.getValue().entrySet()) {
                    HoneyType honey = honeyEntry.getKey();
                    Map<JarType, Integer> jarQuantities = honeyEntry.getValue();

                    log.debug("Reserving for Order #{} [{}] with jars: {}", orderNumber, honey, jarQuantities);
                    Order order = new Order(honey, jarQuantities, orderNumber);

                    try {
                        var result = orchestrator.reserveFor(order);

                        OrderRecord.Status status = result.success()
                                ? OrderRecord.Status.RESERVED
                                : OrderRecord.Status.FAILED;

                        OrderRecord record = new OrderRecord(
                                null,
                                orderNumber,
                                honey,
                                jarQuantities,
                                Instant.now(),
                                status,
                                result.message()
                        );
                        orderRecords.save(record);

                        if (result.success()) {
                            success++;
                            log.info("Order #{} [{}] RESERVED: {}", orderNumber, honey, result.message());
                        } else {
                            failed++;
                            log.warn("Order #{} [{}] FAILED: {}", orderNumber, honey, result.message());
                        }
                    } catch (Exception ex) {
                        failed++;
                        log.error("Order #{} [{}] threw exception during reservation.", orderNumber, honey, ex);
                        try {
                            OrderRecord record = new OrderRecord(
                                    null,
                                    orderNumber,
                                    honey,
                                    jarQuantities,
                                    Instant.now(),
                                    OrderRecord.Status.FAILED,
                                    "EXCEPTION: " + ex.getMessage()
                            );
                            orderRecords.save(record);
                        } catch (Exception ignore) {
                            log.warn("Could not persist FAILED record for order #{} [{}]: {}", orderNumber, honey, ignore.toString());
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
