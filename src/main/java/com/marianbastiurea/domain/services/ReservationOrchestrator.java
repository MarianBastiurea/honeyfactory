package com.marianbastiurea.domain.services;

import com.marianbastiurea.api.dto.ReserveResult;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.AllocationLine;
import com.marianbastiurea.domain.model.AllocationPlan;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.repo.CrateRepo;
import com.marianbastiurea.domain.repo.JarRepo;
import com.marianbastiurea.domain.repo.LabelRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;

import static java.util.Objects.requireNonNull;

@Service
public class ReservationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReservationOrchestrator.class);

    private final AllocationPlanner allocationPlanner;
    private final WarehouseStockGateway stockGateway;

    private final JarRepo jarRepo;
    private final CrateRepo crateRepo;
    private final LabelRepo labelRepo;

    private final TransactionTemplate jarsTT;
    private final TransactionTemplate cratesTT;
    private final TransactionTemplate labelsTT;

    private final ThreadFactory vtFactory;

    public ReservationOrchestrator(AllocationPlanner allocationPlanner,
                                   WarehouseStockGateway stockGateway,
                                   JarRepo jarRepo,
                                   CrateRepo crateRepo,
                                   LabelRepo labelRepo,
                                   @Qualifier("jarsTT")   TransactionTemplate jarsTT,
                                   @Qualifier("cratesTT") TransactionTemplate cratesTT,
                                   @Qualifier("labelsTT") TransactionTemplate labelsTT,
                                   @Qualifier("vtThreadFactory") ThreadFactory vtFactory) {
        this.allocationPlanner = requireNonNull(allocationPlanner, "allocationPlanner");
        this.stockGateway = requireNonNull(stockGateway, "stockGateway");
        this.jarRepo = requireNonNull(jarRepo, "jarRepo");
        this.crateRepo = requireNonNull(crateRepo, "crateRepo");
        this.labelRepo = requireNonNull(labelRepo, "labelRepo");
        this.jarsTT = requireNonNull(jarsTT, "jarsTT");
        this.cratesTT = requireNonNull(cratesTT, "cratesTT");
        this.labelsTT = requireNonNull(labelsTT, "labelsTT");
        this.vtFactory = requireNonNull(vtFactory, "vtFactory");
    }

    public ReservationResult reserveFor(Order order) {
        requireNonNull(order, "order");

        int jarTypes = order.jarQuantities() != null ? order.jarQuantities().size() : 0;
        int totalJars = order.jarQuantities() == null ? 0 :
                order.jarQuantities().values().stream().filter(v -> v != null && v > 0).mapToInt(Integer::intValue).sum();

        log.info("[reserveFor] Start order#{} [{}]: jarTypes={}, totalJars={}",
                order.orderNumber(), order.honeyType(), jarTypes, totalJars);

        long t0 = System.nanoTime();
        try {
            if (order.jarQuantities() == null || order.jarQuantities().isEmpty()) {
                return ReservationResult.failure("Nu s-au specificat borcane pentru comanda #" + order.orderNumber());
            }

            BigDecimal totalKg = jarsToKg(order.jarQuantities());
            log.debug("[reserveFor] Computed totalKg={} for order#{}", totalKg, order.orderNumber());

            BigDecimal pkgCapKg;
            try (var scope = new StructuredTaskScope.ShutdownOnFailure("pkg-cap", vtFactory)) {
                var fJars   = scope.fork(() -> jarRepo.freeAsKg(order.jarQuantities(), order.honeyType()));
                var fCrates = scope.fork(() -> crateRepo.freeAsKg(order.jarQuantities()));
                var fLabels = scope.fork(() -> labelRepo.freeAsKg(order.jarQuantities()));

                scope.join().throwIfFailed();

                // ⬇️ înlocuiește .result() cu .get() (sau .resultNow())
                var jarsCap   = fJars.get();     // sau fJars.resultNow()
                var cratesCap = fCrates.get();   // sau fCrates.resultNow()
                var labelsCap = fLabels.get();   // sau fLabels.resultNow()

                pkgCapKg = min(jarsCap, cratesCap, labelsCap);
                log.info("[reserveFor] Packaging caps kg -> jars={}, crates={}, labels={}, min={}",
                        jarsCap, cratesCap, labelsCap, pkgCapKg);
            }

            if (pkgCapKg.compareTo(totalKg) < 0) {
                log.warn("[reserveFor] NOT ENOUGH PACKAGING for order#{}: needKg={}, haveKg={}",
                        order.orderNumber(), totalKg, pkgCapKg);
                return ReservationResult.failure("Ambalaje insuficiente (borcane/lăzi/etichete) pentru comanda #" + order.orderNumber());
            }

            long tLoad0 = System.nanoTime();
            LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse = loadStocksParallel();
            long tLoadMs = (System.nanoTime() - tLoad0) / 1_000_000;
            log.info("[reserveFor] Loaded stock from {} warehouse(s) in {} ms.", stockByWarehouse.size(), tLoadMs);

            long tPlan0 = System.nanoTime();
            Map<HoneyType, BigDecimal> requestedByType = Map.of(order.honeyType(), totalKg);
            Optional<AllocationPlan> planOpt = allocationPlanner.planAllOrNothing(
                    order.orderNumber(), requestedByType, stockByWarehouse);
            long tPlanMs = (System.nanoTime() - tPlan0) / 1_000_000;

            if (planOpt.isEmpty()) {
                log.warn("[reserveFor] No feasible HONEY plan for order#{} [{}]. Planning took {} ms.",
                        order.orderNumber(), order.honeyType(), tPlanMs);
                return ReservationResult.failure("Stoc de miere insuficient pentru comanda #" + order.orderNumber());
            }

            AllocationPlan plan = planOpt.get();
            log.info("[reserveFor] Plan READY for order#{}: lines={}, plannedKg={}; planning {} ms.",
                    order.orderNumber(), plan.getLines().size(), totalReservedKg(plan), tPlanMs);
            if (log.isTraceEnabled()) {
                for (AllocationLine line : plan.getLines()) {
                    log.trace("  line -> wh='{}', type={}, kg={}", line.warehouseKey(), line.type(), line.quantityKg());
                }
            }

            long tRes0 = System.nanoTime();
            var jarsToReserve = order.jarQuantities();

            try (var scope = new StructuredTaskScope.ShutdownOnFailure("reserve-all", vtFactory)) {
                scope.fork(() -> jarsTT.execute(s -> { jarRepo.reserve(jarsToReserve); return null; }));
                scope.fork(() -> cratesTT.execute(s -> { crateRepo.reserve(jarsToReserve); return null; }));
                scope.fork(() -> labelsTT.execute(s -> { labelRepo.reserve(jarsToReserve); return null; }));

                scope.fork(() -> {
                    ReserveResult rr = stockGateway.reserve(plan);
                    log.debug("[reserveFor] stockGateway.reserve(plan) -> {}", rr);
                    return null;
                });

                scope.join().throwIfFailed();
            }

            long tResMs = (System.nanoTime() - tRes0) / 1_000_000;
            long totalMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("[reserveFor] SUCCESS order#{} [{}]: total={} ms (load={} ms, plan={} ms, reserve={} ms).",
                    order.orderNumber(), order.honeyType(), totalMs, tLoadMs, tPlanMs, tResMs);

            return ReservationResult.success(plan, "Rezervare reușită pentru comanda #" + order.orderNumber());
        } catch (Exception ex) {
            long totalMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("[reserveFor] ERROR order#{} in {} ms: {}", order.orderNumber(), totalMs, ex.getMessage(), ex);
            return ReservationResult.failure("Eroare la rezervare pentru comanda #" + order.orderNumber() + ": " + ex.getMessage());
        }
    }

    private LinkedHashMap<String, Map<HoneyType, BigDecimal>> loadStocksParallel() throws Exception {
        List<String> keys = stockGateway.warehouseKeys();
        if (keys == null || keys.isEmpty()) {
            log.warn("[loadStocksParallel] No warehouse keys provided. Falling back to single-call load.");
            return stockGateway.loadAvailable();
        }

        log.debug("[loadStocksParallel] Loading {} warehouse(s) in parallel (virtual threads).", keys.size());
        try (var scope = new StructuredTaskScope.ShutdownOnFailure("stock-load", vtFactory)) {
            LinkedHashMap<String, StructuredTaskScope.Subtask<Map<HoneyType, BigDecimal>>> subs = new LinkedHashMap<>();
            for (String k : keys) {
                String key = k;
                subs.put(key, scope.fork(() -> {
                    Map<HoneyType, BigDecimal> m = stockGateway.loadAvailableFor(key);
                    if (m == null) m = Map.of();
                    log.trace("[loadStocksParallel] Loaded key='{}' types={}", key, m.size());
                    return m;
                }));
            }
            scope.join().throwIfFailed();

            LinkedHashMap<String, Map<HoneyType, BigDecimal>> out = new LinkedHashMap<>();
            for (String k : keys) {
                out.put(k, subs.get(k).get());
            }
            log.debug("[loadStocksParallel] Done. Aggregated {} warehouse(s).", out.size());
            return out;
        }
    }

    private static BigDecimal totalReservedKg(AllocationPlan plan) {
        return plan.getLines().stream()
                .map(AllocationLine::quantityKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal jarsToKg(Map<JarType, Integer> jarQuantities) {
        if (jarQuantities == null || jarQuantities.isEmpty()) return BigDecimal.ZERO;
        return jarQuantities.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                .map(e -> e.getKey().kgPerJar().multiply(BigDecimal.valueOf(e.getValue().longValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal min(BigDecimal... values) {
        BigDecimal m = null;
        for (BigDecimal v : values) {
            if (v == null) continue;
            m = (m == null) ? v : (v.compareTo(m) < 0 ? v : m);
        }
        return m == null ? BigDecimal.ZERO : m;
    }

    public record ReservationResult(boolean success, String message, AllocationPlan plan) {
        public static ReservationResult success(AllocationPlan plan, String message) {
            return new ReservationResult(true, message, plan);
        }
        public static ReservationResult failure(String message) {
            return new ReservationResult(false, message, null);
        }
    }

    public interface WarehouseStockGateway {
        LinkedHashMap<String, Map<HoneyType, BigDecimal>> loadAvailable();
        default List<String> warehouseKeys() { return List.copyOf(loadAvailable().keySet()); }
        default Map<HoneyType, BigDecimal> loadAvailableFor(String warehouseKey) {
            return loadAvailable().getOrDefault(warehouseKey, Map.of());
        }
        void reserve(String warehouseKey, HoneyType type, BigDecimal quantityKg);
        ReserveResult reserve(AllocationPlan plan);
    }
}
