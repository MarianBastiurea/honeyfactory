package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.api.dto.DeliveryResult;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.repo.HoneyRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Repository
public class HoneyJdbcRepository implements HoneyRepo {

    private static final Logger log = LoggerFactory.getLogger(HoneyJdbcRepository.class);

    private final Map<HoneyType, NamedParameterJdbcTemplate> tplByType;
    private final Map<HoneyType, TransactionTemplate> txByType;
    private final int retryLimit;

    public HoneyJdbcRepository(
            @Qualifier("acaciaTpl")      NamedParameterJdbcTemplate acaciaTpl,
            @Qualifier("rapeseedTpl")    NamedParameterJdbcTemplate rapeseedTpl,
            @Qualifier("wildflowerTpl")  NamedParameterJdbcTemplate wildFlowerTpl,
            @Qualifier("lindenTpl")      NamedParameterJdbcTemplate lindenTpl,
            @Qualifier("sunflowerTpl")   NamedParameterJdbcTemplate sunFlowerTpl,
            @Qualifier("falseindigoTpl") NamedParameterJdbcTemplate falseIndigoTpl,
            @Value("${honey.repo.retries:5}") int retryLimit
    ) {
        this.retryLimit = retryLimit;

        this.tplByType = new EnumMap<>(HoneyType.class);
        tplByType.put(HoneyType.ACACIA,       acaciaTpl);
        tplByType.put(HoneyType.RAPESEED,     rapeseedTpl);
        tplByType.put(HoneyType.WILDFLOWER,   wildFlowerTpl);
        tplByType.put(HoneyType.LINDEN,       lindenTpl);
        tplByType.put(HoneyType.SUNFLOWER,    sunFlowerTpl);
        tplByType.put(HoneyType.FALSE_INDIGO, falseIndigoTpl);

        this.txByType = new EnumMap<>(HoneyType.class);
        tplByType.forEach((type, tpl) -> {
            DataSource ds = requireNonNull(tpl.getJdbcTemplate().getDataSource(), "DataSource missing for " + type);
            var tm = new DataSourceTransactionManager(ds);
            var tt = new TransactionTemplate(tm);
            this.txByType.put(type, tt);

            // logging DS info (fără secrete)
            String dsInfo = ds.getClass().getSimpleName();
            String poolName = tryReflect(ds, "getPoolName");
            String jdbcUrl  = tryReflect(ds, "getJdbcUrl"); // doar pentru debug; nu logăm user/secrete
            log.info("Honey DS wired for {} -> {}{}{}",
                    type,
                    dsInfo,
                    poolName != null ? (" pool=" + poolName) : "",
                    (jdbcUrl != null && log.isDebugEnabled()) ? (" url=" + jdbcUrl) : ""
            );
        });

        log.info("HoneyJdbcRepository initialized. retryLimit={}", this.retryLimit);
    }

    private NamedParameterJdbcTemplate tpl(HoneyType type) {
        return requireNonNull(tplByType.get(type), "No template configured for " + type);
    }

    private TransactionTemplate tx(HoneyType type) {
        return requireNonNull(txByType.get(type), "No transaction template for " + type);
    }

    @Override
    public BigDecimal freeKg(HoneyType type) {
        long t0 = System.nanoTime();
        try {
            BigDecimal v = tpl(type).getJdbcTemplate()
                    .queryForObject("SELECT current_stock FROM public.stock WHERE id = 1", BigDecimal.class);
            if (v == null) v = BigDecimal.ZERO;
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.debug("[freeKg] {} -> {} kg ({} ms)", type, v, tookMs);
            return v.max(BigDecimal.ZERO);
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("[freeKg] FAILED for {} in {} ms: {}", type, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    public DeliveryResult processOrder(HoneyType type, int orderNumber, BigDecimal requestedKg) {
        requireNonNull(type, "type");
        requireNonNull(requestedKg, "requestedKg");
        if (requestedKg.signum() < 0) {
            throw new IllegalArgumentException("requestedKg must be >= 0");
        }

        log.info("[deliver] Start {} order#{} request={} kg (retryLimit={})", type, orderNumber, requestedKg, retryLimit);

        for (int attempt = 1; attempt <= retryLimit; attempt++) {
            long t0 = System.nanoTime();
            DeliveryResult result = tx(type).execute(status -> {
                SqlRowSet rs = tpl(type).getJdbcTemplate()
                        .queryForRowSet("SELECT current_stock, row_version FROM public.stock WHERE id = 1");
                if (!rs.next()) throw new IllegalStateException("Stock row missing (id=1)");

                BigDecimal current = rs.getBigDecimal("current_stock");
                long version = rs.getLong("row_version");
                if (current == null) current = BigDecimal.ZERO;

                BigDecimal delivered = requestedKg.min(current);
                BigDecimal newStock = current.subtract(delivered);

                var p = new MapSqlParameterSource()
                        .addValue("newStock", newStock)
                        .addValue("oldVersion", version);

                int updated = tpl(type).update(
                        "UPDATE public.stock " +
                                "SET current_stock = :newStock, row_version = row_version + 1, last_updated = NOW() " +
                                "WHERE id = 1 AND row_version = :oldVersion",
                        p
                );

                if (updated == 0) {
                    // optimistic-lock collision
                    status.setRollbackOnly();
                    return null;
                }

                var logParams = new MapSqlParameterSource()
                        .addValue("onum", orderNumber)
                        .addValue("req",  requestedKg)
                        .addValue("del",  delivered);

                tpl(type).update(
                        "INSERT INTO public.processing_log(order_number, requested_kg, delivered_kg, reason) " +
                                "VALUES (:onum, :req, :del, 'DELIVER')",
                        logParams
                );

                return new DeliveryResult(delivered, newStock, version + 1);
            });

            long tookMs = (System.nanoTime() - t0) / 1_000_000;

            if (result != null) {
                log.info("[deliver] SUCCESS {} order#{} delivered={} kg, newStock={} kg, newVersion={}, attempt={}, {} ms",
                        type, orderNumber, result.deliveredKg(), result.newStock(), result.newVersion(), attempt, tookMs);
                return result;
            } else {
                log.warn("[deliver] Optimistic lock conflict for {} order#{} (attempt {} of {}). Retrying...",
                        type, orderNumber, attempt, retryLimit);
            }
        }

        log.error("[deliver] FAILED {} order#{}: concurrent stock update, retry limit ({}) reached.",
                type, orderNumber, retryLimit);
        throw new IllegalStateException("Concurrent stock update, retry limit reached for " + type);
    }

    // ---- little reflective helper for nicer DS logs without hard deps on Hikari ----
    private static String tryReflect(Object target, String getter) {
        try {
            var m = target.getClass().getMethod(getter);
            m.setAccessible(true);
            Object v = m.invoke(target);
            return v != null ? String.valueOf(v) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
