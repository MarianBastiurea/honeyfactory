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

@Repository
public class RouterHoneyRepo implements HoneyRepo {

    private static final Logger log = LoggerFactory.getLogger(RouterHoneyRepo.class);

    private final Map<HoneyType, NamedParameterJdbcTemplate> tplByType = new EnumMap<>(HoneyType.class);
    private final Map<HoneyType, TransactionTemplate> txByType = new EnumMap<>(HoneyType.class);
    private final int retryLimit;

    public RouterHoneyRepo(
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
            DataSource ds = Objects.requireNonNull(
                    tpl.getJdbcTemplate().getDataSource(), "Missing DataSource for " + type);
            var tm = new DataSourceTransactionManager(ds);
            txByType.put(type, new TransactionTemplate(tm));
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
    public BigDecimal availableKg(HoneyType type) {
        long t0 = System.nanoTime();
        BigDecimal v = tpl(type).getJdbcTemplate().queryForObject(
                "SELECT COALESCE(final_stock,0) FROM public.stock WHERE id = 1", BigDecimal.class);

        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public DeliveryResult processOrder(HoneyType type, int orderNumber, BigDecimal requestedKg) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(requestedKg, "requestedKg");
        if (requestedKg.signum() < 0) throw new IllegalArgumentException("requestedKg must be >= 0");

        for (int attempt = 1; attempt <= retryLimit; attempt++) {
            DeliveryResult result = tx(type).execute(status -> {
                SqlRowSet rs = tpl(type).getJdbcTemplate().queryForRowSet(
                        "SELECT initial_stock, COALESCE(final_stock,0) AS final_stock, " +
                                "       COALESCE(delivered,0) AS delivered, row_version " +
                                "FROM public.stock WHERE id = 1"
                );
                if (!rs.next()) {
                    throw new IllegalStateException("Stock row missing (id=1)");
                }

                BigDecimal initial = rs.getBigDecimal("initial_stock");
                BigDecimal freeNow = rs.getBigDecimal("final_stock");
                BigDecimal deliveredNow = rs.getBigDecimal("delivered");
                long version = rs.getLong("row_version");

                if (initial == null) initial = BigDecimal.ZERO;
                if (freeNow == null) freeNow = BigDecimal.ZERO;
                if (deliveredNow == null) deliveredNow = BigDecimal.ZERO;

                BigDecimal deliverNow = requestedKg.min(freeNow);
                BigDecimal newDelivered = deliveredNow.add(deliverNow);

                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("orderedNow", requestedKg)
                        .addValue("newDelivered", newDelivered)
                        .addValue("oldVersion", version);

                int updated = tpl(type).update(
                        "UPDATE public.stock " +
                                "SET ordered      = :orderedNow, " +
                                "    delivered    = :newDelivered, " +
                                "    row_version  = row_version + 1, " +
                                "    last_updated = NOW() " +
                                "WHERE id = 1 AND row_version = :oldVersion " +
                                "  AND :newDelivered <= initial_stock",
                        params
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

                BigDecimal newFree = freeNow.subtract(deliverNow);
                if (newFree.signum() < 0) newFree = BigDecimal.ZERO;

                return new DeliveryResult(deliverNow, newFree, version + 1);
            });

            if (result != null) return result;
        }

        throw new IllegalStateException("Concurrent stock update, retry limit reached for " + type);
    }
}
