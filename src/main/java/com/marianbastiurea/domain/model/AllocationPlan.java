package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.HoneyType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AllocationPlan {
    private final UUID planId;
    private final String orderId;
    private final Instant createdAt;

    // Cererea totală pe tip
    private final Map<HoneyType, BigDecimal> requested;

    // Liniile de alocare efective (sursa, tip, cantitate)
    private final List<AllocationLine> lines;

    // Status derivat din requested vs allocated
    private PlanStatus status;

    // Cache derivat (recalculat la nevoie)
    private final Map<HoneyType, BigDecimal> allocatedCache = new ConcurrentHashMap<>();

    private AllocationPlan(UUID planId,
                           String orderId,
                           Instant createdAt,
                           Map<HoneyType, BigDecimal> requested,
                           List<AllocationLine> lines,
                           PlanStatus status) {
        this.planId = Objects.requireNonNull(planId, "planId is required");
        this.orderId = Objects.requireNonNull(orderId, "orderId is required");
        this.createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
        this.requested = Collections.unmodifiableMap(normalizeRequested(requested));
        this.lines = new ArrayList<>(Objects.requireNonNullElseGet(lines, ArrayList::new));
        this.status = Objects.requireNonNullElse(status, PlanStatus.DRAFT);
        recomputeAllocatedCache();
        refreshStatus();
    }

    public static AllocationPlan create(String orderId, Map<HoneyType, BigDecimal> requested) {
        return new AllocationPlan(UUID.randomUUID(), orderId, Instant.now(), requested, List.of(), PlanStatus.DRAFT);
    }

    public UUID getPlanId() {
        return planId;
    }

    public String getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public Map<HoneyType, BigDecimal> getRequested() {
        return requested;
    }

    public List<AllocationLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** Cantitatea alocată până acum pentru un honey type. */
    public BigDecimal getAllocated(HoneyType type) {
        return allocatedCache.getOrDefault(type, BigDecimal.ZERO);
    }

    /** Cantitatea rămasă de alocat pentru un honey type. */
    public BigDecimal getRemaining(HoneyType type) {
        return maxZero(requested.getOrDefault(type, BigDecimal.ZERO).subtract(getAllocated(type)));
    }

    /** Total alocat pe toate tipurile. */
    public BigDecimal getTotalAllocated() {
        return allocatedCache.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total cerut pe toate tipurile. */
    public BigDecimal getTotalRequested() {
        return requested.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** True dacă pentru toate tipurile alocarea a atins cererea. */
    public boolean isFullyAllocated() {
        return status == PlanStatus.FULLY_ALLOCATED;
    }

    /** True dacă nu s-a alocat nimic sau doar parțial. */
    public boolean isPartiallyAllocated() {
        return status == PlanStatus.PARTIALLY_ALLOCATED;
    }

    /**
     * Adaugă o alocare către un “warehouse” (ex.: ACACIA-RDS, URL, sau ID).
     * Validează să nu depășească cantitatea rămasă.
     */
    public synchronized void allocateToWarehouse(HoneyType type, BigDecimal quantityKg, String warehouseKey, String note) {
        requirePositive(quantityKg, "quantityKg");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(warehouseKey, "warehouseKey");

        BigDecimal remaining = getRemaining(type);
        if (remaining.compareTo(quantityKg) < 0) {
            throw new IllegalArgumentException("Allocation exceeds remaining request for " + type +
                    ": remaining=" + remaining + ", tried=" + quantityKg);
        }

        AllocationLine line = new AllocationLine(warehouseKey, type, normalize(quantityKg), Instant.now());
        lines.add(line);
        // update cache
        allocatedCache.merge(type, line.quantityKg(), BigDecimal::add);
        refreshStatus();
    }



    /** Grupare liniilor pe warehouse (util pentru invocarea API-urilor RDS specifice). */
    public Map<String, List<AllocationLine>> groupByWarehouse() {
        return getLines().stream().collect(Collectors.groupingBy(AllocationLine::warehouseKey));
    }

    /** Grupare pe HoneyType. */
    public Map<HoneyType, List<AllocationLine>> groupByHoneyType() {
        return getLines().stream().collect(Collectors.groupingBy(AllocationLine::type));
    }

    private void refreshStatus() {
        boolean allSatisfied = requested.entrySet().stream()
                .allMatch(e -> getAllocated(e.getKey()).compareTo(e.getValue()) >= 0);

        boolean anyAllocated = allocatedCache.values().stream().anyMatch(v -> v.compareTo(BigDecimal.ZERO) > 0);

        if (status != PlanStatus.REJECTED) {
            if (allSatisfied) {
                status = PlanStatus.FULLY_ALLOCATED;
            } else if (anyAllocated) {
                status = PlanStatus.PARTIALLY_ALLOCATED;
            } else {
                status = PlanStatus.DRAFT;
            }
        }
    }

    private void recomputeAllocatedCache() {
        allocatedCache.clear();
        for (AllocationLine l : lines) {
            allocatedCache.merge(l.type(), l.quantityKg(), BigDecimal::add);
        }
        // normalizare la scale
        for (HoneyType t : HoneyType.values()) {
            allocatedCache.put(t, normalize(allocatedCache.getOrDefault(t, BigDecimal.ZERO)));
        }
    }

    private static Map<HoneyType, BigDecimal> normalizeRequested(Map<HoneyType, BigDecimal> req) {
        if (req == null || req.isEmpty()) {
            throw new IllegalArgumentException("requested map must not be null or empty");
        }
        Map<HoneyType, BigDecimal> out = new EnumMap<>(HoneyType.class);
        for (Map.Entry<HoneyType, BigDecimal> e : req.entrySet()) {
            requireNonNegative(e.getValue(), "requested[" + e.getKey() + "]");
            out.put(e.getKey(), normalize(e.getValue()));
        }
        return out;
    }

    private static BigDecimal normalize(BigDecimal v) {
        return v.setScale(3, RoundingMode.HALF_UP); // kg cu 3 zecimale
    }

    private static BigDecimal maxZero(BigDecimal v) {
        return v.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : v;
    }

    private static void requirePositive(BigDecimal v, String field) {
        if (v == null || v.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
    }

    private static void requireNonNegative(BigDecimal v, String field) {
        if (v == null || v.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
    }

    @Override
    public String toString() {
        return "AllocationPlan{" +
                "planId=" + planId +
                ", orderId='" + orderId + '\'' +
                ", status=" + status +
                ", requested=" + requested +
                ", allocated=" + allocatedCache +
                ", lines=" + lines +
                '}';
    }

    public static AllocationPlan of(String orderId,
                                    Map<HoneyType, BigDecimal> requested,
                                    List<AllocationLine> lines) {
        return new AllocationPlan(
                UUID.randomUUID(),
                Objects.requireNonNull(orderId, "orderId"),
                Instant.now(),
                requested,
                lines,
                PlanStatus.DRAFT
        );
    }

    /** Alias semantic: când ai deja liniile calculate. */
    public static AllocationPlan fromComputedLines(String orderId,
                                                   Map<HoneyType, BigDecimal> requested,
                                                   List<AllocationLine> lines) {
        return of(orderId, requested, lines);
    }

}
