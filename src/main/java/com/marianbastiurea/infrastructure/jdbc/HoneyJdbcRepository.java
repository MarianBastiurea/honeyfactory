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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@Repository
public class HoneyJdbcRepository implements HoneyRepo {

    private static final Logger log = LoggerFactory.getLogger(HoneyJdbcRepository.class);

    private final Map<HoneyType, NamedParameterJdbcTemplate> tplByType = new EnumMap<>(HoneyType.class);
    private final Map<HoneyType, TransactionTemplate> txByType = new EnumMap<>(HoneyType.class);
    private final int retryLimit;

    public HoneyJdbcRepository(
            @Qualifier("acaciaTpl") NamedParameterJdbcTemplate acaciaTpl,
            @Qualifier("rapeseedTpl") NamedParameterJdbcTemplate rapeseedTpl,
            @Qualifier("wildflowerTpl") NamedParameterJdbcTemplate wildFlowerTpl,
            @Qualifier("lindenTpl") NamedParameterJdbcTemplate lindenTpl,
            @Qualifier("sunflowerTpl") NamedParameterJdbcTemplate sunFlowerTpl,
            @Qualifier("falseindigoTpl") NamedParameterJdbcTemplate falseIndigoTpl,
            @Value("${honey.repo.retries:5}") int retryLimit
    ) {
        this.retryLimit = retryLimit;

        tplByType.put(HoneyType.ACACIA, acaciaTpl);
        tplByType.put(HoneyType.RAPESEED, rapeseedTpl);
        tplByType.put(HoneyType.WILDFLOWER, wildFlowerTpl);
        tplByType.put(HoneyType.LINDEN, lindenTpl);
        tplByType.put(HoneyType.SUNFLOWER, sunFlowerTpl);
        tplByType.put(HoneyType.FALSE_INDIGO, falseIndigoTpl);

        tplByType.forEach((type, tpl) -> {
            DataSource ds = requireNonNull(tpl.getJdbcTemplate().getDataSource(), "Missing DataSource for " + type);
            var tm = new DataSourceTransactionManager(ds);
            txByType.put(type, new TransactionTemplate(tm));

            String dsInfo = ds.getClass().getSimpleName();
            String poolName = tryReflect(ds, "getPoolName");
            String jdbcUrl = tryReflect(ds, "getJdbcUrl"); // debug only
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
    public BigDecimal availableKg(HoneyType type) {
        Objects.requireNonNull(type, "type");
        BigDecimal v = tpl(type).getJdbcTemplate()
                .queryForObject(
                        "SELECT COALESCE(final_stock, 0) FROM public.stock WHERE id = 1",
                        BigDecimal.class
                );
        if (v == null) v = BigDecimal.ZERO;
        return v.max(BigDecimal.ZERO);
    }

    @Override
    public DeliveryResult processOrder(HoneyType type, int orderNumber, BigDecimal requestedKg) {
        requireNonNull(type, "type");
        requireNonNull(requestedKg, "requestedKg");
        if (requestedKg.signum() < 0) throw new IllegalArgumentException("requestedKg must be >= 0");

        log.info("[deliver] Start {} order#{} request={} kg (retryLimit={})", type, orderNumber, requestedKg, retryLimit);

        for (int attempt = 1; attempt <= retryLimit; attempt++) {
            final long t0 = System.nanoTime();

            DeliveryResult out = tx(type).execute(status -> {
                SqlRowSet rs = tpl(type).getJdbcTemplate().queryForRowSet(
                        "SELECT COALESCE(final_stock,0) AS final_stock, row_version " +
                                "FROM public.stock WHERE id = 1");
                if (!rs.next()) throw new IllegalStateException("Stock row missing (id=1)");

                BigDecimal finalStock = rs.getBigDecimal("final_stock");
                long version = rs.getLong("row_version");
                if (finalStock == null) finalStock = BigDecimal.ZERO;


                BigDecimal deliverNow = requestedKg.min(finalStock);
                if (deliverNow.signum() < 0) deliverNow = BigDecimal.ZERO;


                int updated = tpl(type).update(
                        "UPDATE public.stock " +
                                "SET ordered      = :orderedNow, " +
                                "    delivered    = :deliverNow, " +
                                "    final_stock  = GREATEST(final_stock - :deliverNow, 0), " +
                                "    row_version  = row_version + 1, " +
                                "    last_updated = NOW() " +
                                "WHERE id = 1 AND row_version = :oldVersion " +
                                "  AND :deliverNow <= final_stock",
                        new MapSqlParameterSource()
                                .addValue("orderedNow", requestedKg)
                                .addValue("deliverNow", deliverNow)
                                .addValue("oldVersion", version)
                );

                if (updated == 0) {
                    status.setRollbackOnly();
                    return null;
                }
                tpl(type).update(
                        "INSERT INTO public.processing_log(order_number, requested_kg, delivered_kg, reason) " +
                                "VALUES (:onum, :req, :del, 'DELIVER')",
                        new MapSqlParameterSource()
                                .addValue("onum", orderNumber)
                                .addValue("req", requestedKg)
                                .addValue("del", deliverNow)
                );

                BigDecimal newFinal = finalStock.subtract(deliverNow);
                if (newFinal.signum() < 0) newFinal = BigDecimal.ZERO;
                return new DeliveryResult(deliverNow, newFinal, version + 1);
            });
            if (out != null) {
                return out;
            }
        }
        log.info("[deliver] FAILED {} order#{}: concurrent stock update, retry limit ({}) reached.",
                type, orderNumber, retryLimit);
        throw new IllegalStateException("Concurrent stock update, retry limit reached for " + type);
    }


    private static String tryReflect(Object target, String getter) {
        try {
            var m = target.getClass().getMethod(getter);
            Object v = m.invoke(target);
            return v != null ? String.valueOf(v) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
