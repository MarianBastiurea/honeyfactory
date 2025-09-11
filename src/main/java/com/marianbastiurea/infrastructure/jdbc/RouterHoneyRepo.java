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
import java.util.Objects;

@Repository
public class RouterHoneyRepo implements HoneyRepo {

    private static final Logger log = LoggerFactory.getLogger(RouterHoneyRepo.class);

    private final Map<HoneyType, NamedParameterJdbcTemplate> tplByType = new EnumMap<>(HoneyType.class);
    private final Map<HoneyType, TransactionTemplate> txByType = new EnumMap<>(HoneyType.class);
    private final int retryLimit;

    public RouterHoneyRepo(
            @Qualifier("acaciaTpl")      NamedParameterJdbcTemplate acaciaTpl,
            @Qualifier("rapeseedTpl")    NamedParameterJdbcTemplate rapeseedTpl,
            @Qualifier("wildflowerTpl")  NamedParameterJdbcTemplate wildFlowerTpl,
            @Qualifier("lindenTpl")      NamedParameterJdbcTemplate lindenTpl,
            @Qualifier("sunflowerTpl")   NamedParameterJdbcTemplate sunFlowerTpl,
            @Qualifier("falseindigoTpl") NamedParameterJdbcTemplate falseIndigoTpl,
            @Value("${honey.repo.retries:5}") int retryLimit
    ) {
        this.retryLimit = retryLimit;

        tplByType.put(HoneyType.ACACIA,       acaciaTpl);
        tplByType.put(HoneyType.RAPESEED,     rapeseedTpl);
        tplByType.put(HoneyType.WILDFLOWER,   wildFlowerTpl);
        tplByType.put(HoneyType.LINDEN,       lindenTpl);
        tplByType.put(HoneyType.SUNFLOWER,    sunFlowerTpl);
        tplByType.put(HoneyType.FALSE_INDIGO, falseIndigoTpl);

        tplByType.forEach((type, tpl) -> {
            DataSource ds = Objects.requireNonNull(
                    tpl.getJdbcTemplate().getDataSource(), "Missing DataSource for " + type);
            var tm = new DataSourceTransactionManager(ds);
            txByType.put(type, new TransactionTemplate(tm));

            String dsInfo = ds.getClass().getSimpleName();
            String poolName = tryReflect(ds, "getPoolName");
            String jdbcUrl  = tryReflect(ds, "getJdbcUrl"); // DEBUG only; no secrets
            if (log.isDebugEnabled()) {
                log.debug("Router DS wired for {} -> {}{}{}",
                        type,
                        dsInfo,
                        poolName != null ? (" pool=" + poolName) : "",
                        jdbcUrl  != null ? (" url=" + jdbcUrl)   : ""
                );
            } else {
                log.info("Router DS wired for {} -> {}", type, dsInfo);
            }
        });

        log.info("RouterHoneyRepo initialized. retryLimit={}", this.retryLimit);
    }

    private NamedParameterJdbcTemplate tpl(HoneyType type) {
        return Objects.requireNonNull(tplByType.get(type), "No template for " + type);
    }

    private TransactionTemplate tx(HoneyType type) {
        return Objects.requireNonNull(txByType.get(type), "No tx template for " + type);
    }

    @Override
    public BigDecimal freeKg(HoneyType type) {
        long t0 = System.nanoTime();
        try {
            BigDecimal v = tpl(type).getJdbcTemplate()
                    .queryForObject("SELECT initial_stock FROM public.stock WHERE id = 1", BigDecimal.class);
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
        Objects.requireNonNull(requestedKg, "requestedKg");
        if (requestedKg.signum() < 0) throw new IllegalArgumentException("requestedKg must be >= 0");
        log.info("[deliver/router] Start {} order#{} request={} kg (retryLimit={})",
                type, orderNumber, requestedKg, retryLimit);

        for (int attempt = 1; attempt <= retryLimit; attempt++) {
            long t0 = System.nanoTime();
            DeliveryResult result = tx(type).execute(status -> {
                SqlRowSet rs = tpl(type).getJdbcTemplate()
                        .queryForRowSet("SELECT initial_stock, row_version FROM public.stock WHERE id = 1");
                if (!rs.next()) throw new IllegalStateException("Stock row missing (id=1)");

                BigDecimal current = rs.getBigDecimal("initial_stock");
                long version = rs.getLong("row_version");
                if (current == null) current = BigDecimal.ZERO;

                BigDecimal delivered = requestedKg.min(current);
                BigDecimal newStock  = current.subtract(delivered);

                int updated = tpl(type).update(
                        "UPDATE public.stock " +
                                "SET initial_stock = :newStock, row_version = row_version + 1, last_updated = NOW() " +
                                "WHERE id = 1 AND row_version = :oldVersion",
                        new MapSqlParameterSource()
                                .addValue("newStock", newStock)
                                .addValue("oldVersion", version)
                );
                if (updated == 0) {
                    status.setRollbackOnly();
                    return null; // optimistic lock conflict
                }

                tpl(type).update(
                        "INSERT INTO public.processing_log(order_number, requested_kg, delivered_kg, reason) " +
                                "VALUES (:onum, :req, :del, 'DELIVER')",
                        new MapSqlParameterSource()
                                .addValue("onum", orderNumber)
                                .addValue("req",  requestedKg)
                                .addValue("del",  delivered)
                );

                return new DeliveryResult(delivered, newStock, version + 1);
            });

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            if (result != null) {
                log.info("[deliver/router] SUCCESS {} order#{} delivered={} kg, newStock={} kg, newVersion={}, attempt={}, {} ms",
                        type, orderNumber, result.deliveredKg(), result.newStock(), result.newVersion(), attempt, tookMs);
                return result;
            } else {
                log.warn("[deliver/router] Optimistic lock conflict for {} order#{} (attempt {} of {}). Retrying...",
                        type, orderNumber, attempt, retryLimit);
            }
        }

        log.error("[deliver/router] FAILED {} order#{}: concurrent stock update, retry limit ({}) reached.",
                type, orderNumber, retryLimit);
        throw new IllegalStateException("Concurrent stock update, retry limit reached for " + type);
    }


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
