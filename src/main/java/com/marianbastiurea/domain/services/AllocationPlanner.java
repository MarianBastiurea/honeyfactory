package com.marianbastiurea.domain.services;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.model.AllocationLine;
import com.marianbastiurea.domain.model.AllocationPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class AllocationPlanner {

    private static final Logger log = LoggerFactory.getLogger(AllocationPlanner.class);


    public AllocationPlan planGreedy(
            int orderNumber,
            Map<HoneyType, BigDecimal> requestedByType,
            LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse
    ) {
        Objects.requireNonNull(requestedByType, "requestedByType");
        Objects.requireNonNull(stockByWarehouse, "stockByWarehouse");

        long t0 = System.nanoTime();
        if (log.isInfoEnabled()) {
            log.info("Start greedy allocation for order={} | requestedTypes={} | warehouses={}",
                    orderNumber, requestedByType.keySet(), stockByWarehouse.keySet());
        }

        LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockCopy = deepCopy(stockByWarehouse);
        if (log.isDebugEnabled()) {
            log.debug("Deep-copied stock snapshot: {}", stockCopy);
        }

        List<AllocationLine> lines = new ArrayList<>();

        for (Map.Entry<HoneyType, BigDecimal> req : requestedByType.entrySet()) {
            HoneyType type = req.getKey();
            BigDecimal remaining = nz(req.getValue());
            if (log.isDebugEnabled()) {
                log.debug("Processing type={} | requested={}", type, remaining);
            }
            if (remaining.signum() <= 0) {
                if (log.isTraceEnabled()) log.trace("Skip type={} because requested <= 0", type);
                continue;
            }

            for (Map.Entry<String, Map<HoneyType, BigDecimal>> wh : stockCopy.entrySet()) {
                if (remaining.signum() == 0) {
                    if (log.isTraceEnabled()) log.trace("Type={} fully satisfied. Breaking warehouses loop.", type);
                    break;
                }

                String warehouseKey = wh.getKey();
                Map<HoneyType, BigDecimal> whStock = wh.getValue();
                BigDecimal available = nz(whStock.get(type));

                if (log.isTraceEnabled()) {
                    log.trace("Warehouse={} | type={} | available={} | remaining={}",
                            warehouseKey, type, available, remaining);
                }

                if (available.signum() <= 0) {
                    if (log.isTraceEnabled()) log.trace("Warehouse={} has no stock for type={}", warehouseKey, type);
                    continue;
                }

                BigDecimal take = min(available, remaining);
                if (take.signum() > 0) {
                    lines.add(new AllocationLine(warehouseKey, type, take, Instant.now()));
                    whStock.put(type, available.subtract(take));
                    remaining = remaining.subtract(take);

                    if (log.isDebugEnabled()) {
                        log.debug("Allocated {} kg of {} from warehouse={} | remaining for type={} is {}",
                                take, type, warehouseKey, type, remaining);
                    }
                }
            }

            if (remaining.signum() > 0) {
                log.warn("Order={} | type={} NOT fully satisfied by greedy planning. Missing={}",
                        orderNumber, type, remaining);
            }
        }

        AllocationPlan plan = AllocationPlan.of(orderNumber, requestedByType, lines);

        // sumarizare rapidă pentru loguri
        EnumMap<HoneyType, BigDecimal> allocatedByType = new EnumMap<>(HoneyType.class);
        for (AllocationLine line : lines) {
            allocatedByType.merge(line.type(), line.quantityKg(), BigDecimal::add);
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("Completed greedy allocation for order={} in {} ms | lines={} | allocatedByType={}",
                orderNumber, elapsedMs, lines.size(), allocatedByType);

        // semnalăm tipurile neacoperite (dacă există)
        for (Map.Entry<HoneyType, BigDecimal> req : requestedByType.entrySet()) {
            HoneyType type = req.getKey();
            BigDecimal requested = nz(req.getValue());
            BigDecimal allocated = nz(allocatedByType.get(type));
            if (allocated.compareTo(requested) < 0) {
                log.warn("Order={} | type={} under-allocated: requested={} vs allocated={}",
                        orderNumber, type, requested, allocated);
            } else if (log.isTraceEnabled()) {
                log.trace("Order={} | type={} fully satisfied: requested={} == allocated={}",
                        orderNumber, type, requested, allocated);
            }
        }

        return plan;
    }

    /**
     * Variantă “all-or-nothing”: dacă nu putem satisface 100% din cerere, întoarcem Optional.empty().
     */
    public Optional<AllocationPlan> planAllOrNothing(
            int orderNumber,
            Map<HoneyType, BigDecimal> requestedByType,
            LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse
    ) {
        Objects.requireNonNull(requestedByType, "requestedByType");
        Objects.requireNonNull(stockByWarehouse, "stockByWarehouse");

        log.info("Start all-or-nothing allocation check for order={}", orderNumber);

        boolean feasible = canFulfillAll(requestedByType, stockByWarehouse);
        if (!feasible) {
            log.warn("All-or-nothing failed for order={} | Not enough total stock per type to cover request.", orderNumber);
            return Optional.empty();
        }

        log.info("All-or-nothing feasible for order={} | Building greedy plan.", orderNumber);
        return Optional.of(planGreedy(orderNumber, requestedByType, stockByWarehouse));
    }

    /** Verifică dacă totalul stocurilor pe tip acoperă integral cererea. */
    public boolean canFulfillAll(
            Map<HoneyType, BigDecimal> requestedByType,
            Map<String, Map<HoneyType, BigDecimal>> stockByWarehouse
    ) {
        Objects.requireNonNull(requestedByType, "requestedByType");
        Objects.requireNonNull(stockByWarehouse, "stockByWarehouse");

        EnumMap<HoneyType, BigDecimal> totals = new EnumMap<>(HoneyType.class);
        for (Map<HoneyType, BigDecimal> wh : stockByWarehouse.values()) {
            if (wh == null) continue;
            for (Map.Entry<HoneyType, BigDecimal> e : wh.entrySet()) {
                totals.merge(e.getKey(), nz(e.getValue()), BigDecimal::add);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Total stock per type computed: {}", totals);
            log.debug("Requested per type: {}", requestedByType);
        }

        for (Map.Entry<HoneyType, BigDecimal> req : requestedByType.entrySet()) {
            BigDecimal have = nz(totals.get(req.getKey()));
            BigDecimal need = nz(req.getValue());
            if (have.compareTo(need) < 0) {
                log.debug("Feasibility check failed for type={} | have={} < need={}", req.getKey(), have, need);
                return false;
            }
        }
        return true;
    }

    // ---------- helpers ----------
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static LinkedHashMap<String, Map<HoneyType, BigDecimal>> deepCopy(
            LinkedHashMap<String, Map<HoneyType, BigDecimal>> src
    ) {
        LinkedHashMap<String, Map<HoneyType, BigDecimal>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<HoneyType, BigDecimal>> e : src.entrySet()) {
            EnumMap<HoneyType, BigDecimal> inner = new EnumMap<>(HoneyType.class);
            Map<HoneyType, BigDecimal> original = e.getValue();
            if (original != null) {
                for (Map.Entry<HoneyType, BigDecimal> v : original.entrySet()) {
                    inner.put(v.getKey(), nz(v.getValue()));
                }
            }
            copy.put(e.getKey(), inner);
        }
        if (log.isTraceEnabled()) {
            log.trace("Deep copy created for {} warehouses.", copy.size());
        }
        return copy;
    }
}
