package com.marianbastiurea.domain.services;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.AllocationLine;
import com.marianbastiurea.domain.model.AllocationPlan;
import com.marianbastiurea.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.StructuredTaskScope;

@Service
public class ReservationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReservationOrchestrator.class);

    private final AllocationPlanner allocationPlanner;
    private final WarehouseStockGateway stockGateway;

    public ReservationOrchestrator(AllocationPlanner allocationPlanner,
                                   WarehouseStockGateway stockGateway) {
        this.allocationPlanner = allocationPlanner;
        this.stockGateway = stockGateway;
    }

    @Transactional
    public ReservationResult reserveFor(Order order) {
        try {
            BigDecimal totalKg = jarsToKg(order.jarQuantities());
            Map<HoneyType, BigDecimal> requestedByType = Map.of(order.honeyType(), totalKg);

            LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse = loadStocksParallel();

            Optional<AllocationPlan> planOpt =
                    allocationPlanner.planAllOrNothing(order, requestedByType, stockByWarehouse);

            if (planOpt.isEmpty()) {
                return ReservationResult.failure("Stoc insuficient pentru a onora comanda #" + order.orderNumber());
            }

            AllocationPlan plan = planOpt.get();

            for (AllocationLine line : plan.getLines()) {
                stockGateway.reserve(line.warehouseKey(), line.type(), line.quantityKg());
            }

            return ReservationResult.success(plan, "Rezervare reușită pentru comanda #" + order.orderNumber());
        } catch (Exception ex) {
            log.error("[reserveFor] Eroare la rezervare pentru order#{}: {}", order.orderNumber(), ex.toString(), ex);
            return ReservationResult.failure("Eroare la rezervare pentru comanda #" + order.orderNumber() + ": " + ex.getMessage());
        }
    }

    private LinkedHashMap<String, Map<HoneyType, BigDecimal>> loadStocksParallel() throws Exception {
        List<String> keys = stockGateway.warehouseKeys();
        if (keys == null || keys.isEmpty()) {
            return stockGateway.loadAvailable(); // fallback
        }

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            LinkedHashMap<String, StructuredTaskScope.Subtask<Map<HoneyType, BigDecimal>>> subs = new LinkedHashMap<>();
            for (String k : keys) {
                subs.put(k, scope.fork(() -> stockGateway.loadAvailableFor(k)));
            }
            scope.join().throwIfFailed();

            LinkedHashMap<String, Map<HoneyType, BigDecimal>> out = new LinkedHashMap<>();
            for (String k : keys) {
                out.put(k, subs.get(k).get()); // după throwIfFailed(), get() e sigur
            }
            return out;
        }
    }

    private static BigDecimal jarsToKg(Map<JarType, Integer> jarQuantities) {
        if (jarQuantities == null || jarQuantities.isEmpty()) return BigDecimal.ZERO;
        return jarQuantities.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                .map(e -> e.getKey().kgPerJar().multiply(BigDecimal.valueOf(e.getValue().longValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

        default List<String> warehouseKeys() {
            return List.copyOf(loadAvailable().keySet());
        }

        default Map<HoneyType, BigDecimal> loadAvailableFor(String warehouseKey) {
            return loadAvailable().getOrDefault(warehouseKey, Map.of());
        }

        void reserve(String warehouseKey, HoneyType type, BigDecimal quantityKg);
    }

    public interface AllocationPlanner {
        Optional<AllocationPlan> planAllOrNothing(
                Order order,
                Map<HoneyType, BigDecimal> requestedByType,
                LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse
        );
    }
}
