package com.marianbastiurea.domain.services;

import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.repo.CrateRepo;
import com.marianbastiurea.domain.repo.HoneyRepo;
import com.marianbastiurea.domain.repo.JarRepo;
import com.marianbastiurea.domain.repo.LabelRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;

import static java.util.Objects.requireNonNull;

@Service
public class StockSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(StockSnapshotService.class);

    private final HoneyRepo honey;
    private final JarRepo jars;
    private final LabelRepo labels;
    private final CrateRepo crates;
    private final ThreadFactory tf; // virtual threads

    public StockSnapshotService(
            @Qualifier("routerHoneyRepo") HoneyRepo honey,
            JarRepo jars,
            LabelRepo labels,
            CrateRepo crates,
            @Qualifier("vtThreadFactory") ThreadFactory tf
    ) {
        this.honey = requireNonNull(honey, "honey");
        this.jars = requireNonNull(jars, "jars");
        this.labels = requireNonNull(labels, "labels");
        this.crates = requireNonNull(crates, "crates");
        this.tf = requireNonNull(tf, "tf");
    }

    public BigDecimal computeAllocatableKg(Order order) throws Exception {
        requireNonNull(order, "order");
        log.info("[alloc] Start computeAllocatableKg for order#{} [{}] with jars={}",
                order.orderNumber(), order.honeyType(), order.jarQuantities());

        long t0 = System.nanoTime();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure("alloc-scope", tf)) {
            var tHoney  = scope.fork(() -> timedHoney(order));
            var tJars   = scope.fork(() -> timedJars(order));
            var tLabels = scope.fork(() -> timedLabels(order));
            var tCrates = scope.fork(() -> timedCrates(order));

            scope.join().throwIfFailed();

            BigDecimal honeyKg  = nonNeg(tHoney.get());
            BigDecimal jarsKg   = nonNeg(tJars.get());
            BigDecimal labelsKg = nonNeg(tLabels.get());
            BigDecimal cratesKg = nonNeg(tCrates.get());

            BigDecimal min = min(honeyKg, jarsKg, labelsKg, cratesKg);

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[alloc] Done for order#{}: honey={}, jars={}, labels={}, crates={} -> allocatable={} kg ({} ms)",
                    order.orderNumber(), honeyKg, jarsKg, labelsKg, cratesKg, min, tookMs);

            return min;
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("[alloc] FAILED for order#{} in {} ms: {}", order.orderNumber(), tookMs, ex.getMessage(), ex);
            throw ex;
        }
    }


    private BigDecimal timedHoney(Order order) {
        long t = System.nanoTime();
        try {
            BigDecimal v = honey.freeKg(order.honeyType());
            log.debug("[alloc.honey] freeKg({}) -> {} kg ({} ms)",
                    order.honeyType(), v, (System.nanoTime() - t) / 1_000_000);
            return v;
        } catch (Exception ex) {
            log.error("[alloc.honey] ERROR: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    private BigDecimal timedJars(Order order) {
        long t = System.nanoTime();
        try {
            BigDecimal v = jars.freeAsKg(order.jarQuantities(), order.honeyType());
            log.debug("[alloc.jars] freeAsKg(jars={}, honey={}) -> {} kg ({} ms)",
                    order.jarQuantities(), order.honeyType(), v, (System.nanoTime() - t) / 1_000_000);
            return v;
        } catch (Exception ex) {
            log.error("[alloc.jars] ERROR: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    private BigDecimal timedLabels(Order order) {
        long t = System.nanoTime();
        try {
            BigDecimal v = labels.freeAsKg(order.jarQuantities());
            log.debug("[alloc.labels] freeAsKg(jars={}) -> {} kg ({} ms)",
                    order.jarQuantities(), v, (System.nanoTime() - t) / 1_000_000);
            return v;
        } catch (Exception ex) {
            log.error("[alloc.labels] ERROR: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    private BigDecimal timedCrates(Order order) {
        long t = System.nanoTime();
        try {
            BigDecimal v = crates.freeAsKg(order.jarQuantities());
            log.debug("[alloc.crates] freeAsKg(jars={}) -> {} kg ({} ms)",
                    order.jarQuantities(), v, (System.nanoTime() - t) / 1_000_000);
            return v;
        } catch (Exception ex) {
            log.error("[alloc.crates] ERROR: {}", ex.getMessage(), ex);
            throw ex;
        }
    }


    private static BigDecimal nonNeg(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.max(BigDecimal.ZERO);
    }

    private static BigDecimal min(BigDecimal... v) {
        BigDecimal m = v[0];
        for (int i = 1; i < v.length; i++) {
            if (v[i].compareTo(m) < 0) m = v[i];
        }
        return m.max(BigDecimal.ZERO);
    }
}
