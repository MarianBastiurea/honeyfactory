package com.marianbastiurea.domain.services;


import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.model.AllocationLine;
import com.marianbastiurea.domain.model.AllocationPlan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static java.math.BigDecimal.ZERO;
import static java.util.Objects.requireNonNull;

/**
 * Alocare simplă (greedy) din stocurile magaziilor, în ordinea primită.
 * Nu face rezervări, nu are retry, doar calculează liniile de alocare.
 */
public class AllocationPlanner {

    /**
     * @param requestedByType     cererea pe tipuri (kg) – ex: {ACACIA=1500, LINDEN=700}
     * @param stockByWarehouse    stocul per magazie și tip – ex:
     *                            { "WH-A" -> {ACACIA=1000, LINDEN=400},
     *                              "WH-B" -> {ACACIA=900,  LINDEN=500} }
     *                            Folosește LinkedHashMap ca să păstrezi ordinea magaziilor.
     */
    public AllocationPlan planGreedy(int orderNumber,
                                     Map<HoneyType, BigDecimal> requestedByType,
                                     LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse) {
        requireNonNull(requestedByType, "requestedByType");
        requireNonNull(stockByWarehouse, "stockByWarehouse");

        LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockCopy = deepCopy(stockByWarehouse);
        List<AllocationLine> lines = new ArrayList<>();

        for (Map.Entry<HoneyType, BigDecimal> req : requestedByType.entrySet()) {
            HoneyType type = req.getKey();
            BigDecimal remaining = nz(req.getValue());
            if (remaining.signum() <= 0) continue;

            for (Map.Entry<String, Map<HoneyType, BigDecimal>> wh : stockCopy.entrySet()) {
                if (remaining.signum() == 0) break;

                String warehouseKey = wh.getKey();
                Map<HoneyType, BigDecimal> whStock = wh.getValue();

                BigDecimal available = nz(whStock.get(type));
                if (available.signum() <= 0) continue;

                BigDecimal take = min(available, remaining);
                if (take.signum() > 0) {
                    lines.add(new AllocationLine(warehouseKey, type, take, Instant.now()));
                    whStock.put(type, available.subtract(take));
                    remaining = remaining.subtract(take);
                }
            }
        }

        // folosim orderNumber ca orderId (stringificat pentru AllocationPlan)
        String orderId = "ORDER-" + orderNumber;

        return AllocationPlan.of(orderId, requestedByType, lines);
    }


    /**
     * Variantă “all-or-nothing”: dacă nu putem satisface 100% din cerere, întoarcem Optional.empty().
     */
    public Optional<AllocationPlan> planAllOrNothing(int orderNumber,
                                                     Map<HoneyType, BigDecimal> requestedByType,
                                                     LinkedHashMap<String, Map<HoneyType, BigDecimal>> stockByWarehouse) {
        // Verificare rapidă de fezabilitate (total stoc pe tip ≥ total cerere pe tip)
        if (!canFulfillAll(requestedByType, stockByWarehouse)) {
            return Optional.empty();
        }
        // Dacă este fezabil, construim planul complet folosind aceeași logică greedy
        return Optional.of(planGreedy(orderNumber, requestedByType, stockByWarehouse));
    }


    /** Verifică dacă totalul stocurilor pe tip acoperă integral cererea. */
    public boolean canFulfillAll(Map<HoneyType, BigDecimal> requestedByType,
                                 Map<String, Map<HoneyType, BigDecimal>> stockByWarehouse) {
        EnumMap<HoneyType, BigDecimal> totals = new EnumMap<>(HoneyType.class);
        for (Map<HoneyType, BigDecimal> wh : stockByWarehouse.values()) {
            for (Map.Entry<HoneyType, BigDecimal> e : wh.entrySet()) {
                totals.merge(e.getKey(), nz(e.getValue()), BigDecimal::add);
            }
        }
        for (Map.Entry<HoneyType, BigDecimal> req : requestedByType.entrySet()) {
            BigDecimal have = nz(totals.get(req.getKey()));
            if (have.compareTo(nz(req.getValue())) < 0) return false;
        }
        return true;
    }

    // ---------- helpers ----------
    private static BigDecimal nz(BigDecimal v) { return v == null ? ZERO : v; }
    private static BigDecimal min(BigDecimal a, BigDecimal b) { return a.compareTo(b) <= 0 ? a : b; }

    private static LinkedHashMap<String, Map<HoneyType, BigDecimal>> deepCopy(
            LinkedHashMap<String, Map<HoneyType, BigDecimal>> src) {
        LinkedHashMap<String, Map<HoneyType, BigDecimal>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<HoneyType, BigDecimal>> e : src.entrySet()) {
            EnumMap<HoneyType, BigDecimal> inner = new EnumMap<>(HoneyType.class);
            if (e.getValue() != null) {
                for (Map.Entry<HoneyType, BigDecimal> v : e.getValue().entrySet()) {
                    inner.put(v.getKey(), nz(v.getValue()));
                }
            }
            copy.put(e.getKey(), inner);
        }
        return copy;
    }
}
