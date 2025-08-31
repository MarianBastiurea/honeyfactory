package com.marianbastiurea.domain.services;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.AllocationLine;
import com.marianbastiurea.domain.model.AllocationPlan;
import com.marianbastiurea.domain.model.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReservationOrchestrator {

    private final AllocationPlanner allocationPlanner;
    private final WarehouseStockGateway stockGateway;

    public ReservationOrchestrator(AllocationPlanner allocationPlanner,
                                   WarehouseStockGateway stockGateway) {
        this.allocationPlanner = allocationPlanner;
        this.stockGateway = stockGateway;
    }

    /**
     * Orchestrare simplă:
     * 1) citește stocurile agregate pe depozite
     * 2) cere un plan all-or-nothing de la AllocationPlanner
     * 3) aplică rezervările (una câte una) într-o tranzacție
     */
    @Transactional
    public ReservationResult reserveFor(Order order) {
        // 1) Transform cererea în kg per HoneyType (presupunând 1 kg / borcan)
        BigDecimal totalKg = jarsToKg(order.jarQuantities());
        Map<HoneyType, BigDecimal> requestedByType = Map.of(order.honeyType(), totalKg);

        // 2) Încarcă stocurile disponibile pe fiecare depozit
        LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse = stockGateway.loadAvailable();

        // 3) Cere plan all-or-nothing
        Optional<AllocationPlan> planOpt =
                allocationPlanner.planAllOrNothing(requestedByType, stockByWarehouse);

        if (planOpt.isEmpty()) {
            return ReservationResult.failure("Stoc insuficient pentru a onora comanda #" + order.orderNumber());
        }

        AllocationPlan plan = planOpt.get();

        // 4) Aplică rezervările conform planului (tranzacțional)
        for (AllocationLine line : plan.lines()) {
            stockGateway.reserve(line.warehouseKey(), line.type(), line.quantityKg());
        }

        return ReservationResult.success(plan, "Rezervare reușită pentru comanda #" + order.orderNumber());
    }

    private static BigDecimal jarsToKg(Map<JarType, Integer> jarQuantities) {
        return jarQuantities.entrySet().stream()
                .map(e -> e.getKey().getWeightKg().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    // ===== Rezultat simplu, ușor de serializat / logat =====
    public record ReservationResult(boolean success, String message, AllocationPlan plan) {
        public static ReservationResult success(AllocationPlan plan, String message) {
            return new ReservationResult(true, message, plan);
        }
        public static ReservationResult failure(String message) {
            return new ReservationResult(false, message, null);
        }
    }

    // ===== Port (gateway) minim pentru stocuri; implementarea concretă stă în persistence/sql =====
    public interface WarehouseStockGateway {
        /**
         * Returnează stocurile ordonate stabil (LinkedHashMap) pentru un plan determinist:
         *  { "WH-1" -> {ACACIA: 1200, ...}, "WH-2" -> {...}, ... }
         */
        LinkedHashMap<String, Map<HoneyType, BigDecimal>> loadAvailable();

        /**
         * Scade (rezervă) cantitatea din depozit pentru tipul de miere.
         * Implementarea ar trebui să verifice stocul curent și să arunce excepție dacă nu ajunge,
         * lăsând tranzacția să facă rollback.
         */
        void reserve(String warehouseKey, HoneyType type, BigDecimal quantityKg);
    }

    // ===== Port minimal pentru planner; îl ai deja în proiect, păstrat aici ca referință =====
    public interface AllocationPlanner {
        Optional<AllocationPlan> planAllOrNothing(
                Map<HoneyType, BigDecimal> requestedByType,
                LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse
        );
    }
}
