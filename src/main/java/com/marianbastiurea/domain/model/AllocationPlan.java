package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.CrateType;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Plan de alocare pentru MIERE (kg) la nivel de comandă (orderNumber).
 * - Calculează bottleneck-ul dintre miere/borcane/etichete/lăzi
 * - Creează alocarea de miere (AllocationLine) și pick-lists (PrepCommand) către RDS-uri.
 */
public class AllocationPlan {

    private static final Logger log = LoggerFactory.getLogger(AllocationPlan.class);

    private final Integer orderNumber;
    private final Instant createdAt;

    // Cererea totală pe tip (kg). De regulă o singură cheie (honeyType din Order).
    private final Map<HoneyType, BigDecimal> requested;

    // Liniile de alocare efective (sursa, tip, cantitate)
    private final List<AllocationLine> lines;

    private PlanStatus status;

    private final Map<HoneyType, BigDecimal> allocatedCache = new ConcurrentHashMap<>();

    private AllocationPlan(Integer orderNumber,
                           Instant createdAt,
                           Map<HoneyType, BigDecimal> requested,
                           List<AllocationLine> lines,
                           PlanStatus status) {
        this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber is required");
        this.createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
        this.requested = Collections.unmodifiableMap(normalizeRequested(requested));
        this.lines = new ArrayList<>(Objects.requireNonNullElseGet(lines, ArrayList::new));
        this.status = Objects.requireNonNullElse(status, PlanStatus.DRAFT);

        log.info("[AllocationPlan#create] orderNumber={}, createdAt={}", this.orderNumber, this.createdAt);
        log.debug("[AllocationPlan#create] requested(kg)={}", this.requested);

        recomputeAllocatedCache();
        refreshStatus();
    }

    public static AllocationPlan create(Integer orderNumber, Map<HoneyType, BigDecimal> requested) {
        log.debug("[create] orderNumber={}, requested={}", orderNumber, requested);
        return new AllocationPlan(orderNumber, Instant.now(), requested, List.of(), PlanStatus.DRAFT);
    }

    public static AllocationPlan createFromOrder(Order order, Map<HoneyType, BigDecimal> requested) {
        Objects.requireNonNull(order, "order");
        log.debug("[createFromOrder] orderNumber={}, requested={}", order.orderNumber(), requested);
        return new AllocationPlan(order.orderNumber(), Instant.now(), requested, List.of(), PlanStatus.DRAFT);
    }

    public Integer getOrderNumber() { return orderNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public PlanStatus getStatus() { return status; }
    public Map<HoneyType, BigDecimal> getRequested() { return requested; }
    public List<AllocationLine> getLines() { return Collections.unmodifiableList(lines); }

    public BigDecimal getAllocated(HoneyType type) {
        BigDecimal v = allocatedCache.getOrDefault(type, BigDecimal.ZERO);
        log.trace("[getAllocated] type={}, allocatedKg={}", type, v);
        return v;
    }

    public BigDecimal getRemaining(HoneyType type) {
        BigDecimal remaining = maxZero(requested.getOrDefault(type, BigDecimal.ZERO).subtract(getAllocated(type)));
        log.trace("[getRemaining] type={}, remainingKg={}", type, remaining);
        return remaining;
    }

    public BigDecimal getTotalAllocated() {
        BigDecimal sum = allocatedCache.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        log.trace("[getTotalAllocated] totalAllocatedKg={}", sum);
        return sum;
    }

    public BigDecimal getTotalRequested() {
        BigDecimal sum = requested.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        log.trace("[getTotalRequested] totalRequestedKg={}", sum);
        return sum;
    }

    public boolean isFullyAllocated() { return status == PlanStatus.FULLY_ALLOCATED; }
    public boolean isPartiallyAllocated() { return status == PlanStatus.PARTIALLY_ALLOCATED; }

    /** Adaugă o alocare (kg) de miere către un warehouseKey (ex.: ACACIA-RDS). */
    public synchronized void allocateToWarehouse(HoneyType type, BigDecimal quantityKg, String warehouseKey, String note) {
        log.info("[allocateToWarehouse] TRY type={}, qtyKg={}, warehouseKey={}, note={}",
                type, quantityKg, warehouseKey, note);
        requirePositive(quantityKg, "quantityKg");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(warehouseKey, "warehouseKey");

        BigDecimal remaining = getRemaining(type);
        if (remaining.compareTo(quantityKg) < 0) {
            log.error("[allocateToWarehouse] EXCEEDS remaining: type={}, remainingKg={}, triedKg={}",
                    type, remaining, quantityKg);
            throw new IllegalArgumentException("Allocation exceeds remaining request for " + type +
                    ": remaining=" + remaining + ", tried=" + quantityKg);
        }

        AllocationLine line = new AllocationLine(warehouseKey, type, normalize(quantityKg), Instant.now());
        lines.add(line);
        allocatedCache.merge(type, line.quantityKg(), BigDecimal::add);

        log.info("[allocateToWarehouse] OK type={}, allocatedKg={}, warehouseKey={}",
                type, line.quantityKg(), warehouseKey);
        refreshStatus();
    }

    public Map<String, List<AllocationLine>> groupByWarehouse() {
        Map<String, List<AllocationLine>> g = getLines().stream().collect(Collectors.groupingBy(AllocationLine::warehouseKey));
        log.trace("[groupByWarehouse] groups={}", g.keySet());
        return g;
    }

    public Map<HoneyType, List<AllocationLine>> groupByHoneyType() {
        Map<HoneyType, List<AllocationLine>> g = getLines().stream().collect(Collectors.groupingBy(AllocationLine::type));
        log.trace("[groupByHoneyType] groups={}", g.keySet());
        return g;
    }

    // ================== PLANIFICARE ==================

    /**
     * MIN per JarType dintre: requested(borcane), jars stock, labels stock, crates capacity(în borcane),
     * apoi limită GLOBALĂ de miere (kg). Emit:
     *  - AllocationLine (kg) în warehouse-ul corect de miere (în funcție de HoneyType),
     *  - PrepCommand pentru JARS/LABELS/CRATES.
     *
     * @param honeyWarehouseByType map cu cele 6 RDS, ex: ACACIA->"RDS-HONEY-ACACIA", ...
     */
    public static PlanWithPrep planAndPrepareFromOrder(
            Order order,
            PackagingResources stock,
            Map<HoneyType, String> honeyWarehouseByType,
            String jarsWarehouseKey,
            String labelsWarehouseKey,
            String cratesWarehouseKey,
            Optional<PreparationDispatcher> dispatcherOpt
    ) {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(honeyWarehouseByType, "honeyWarehouseByType");
        Objects.requireNonNull(jarsWarehouseKey, "jarsWarehouseKey");
        Objects.requireNonNull(labelsWarehouseKey, "labelsWarehouseKey");
        Objects.requireNonNull(cratesWarehouseKey, "cratesWarehouseKey");

        log.info("[planAndPrepareFromOrder] START orderNumber={}, honeyType={}",
                order.orderNumber(), order.honeyType());
        log.debug("[planAndPrepareFromOrder] requestedJars={}", order.jarQuantities());

        // 1) cererea (borcane pe tip)
        Map<JarType, Integer> requestedJars = new EnumMap<>(JarType.class);
        requestedJars.putAll(order.jarQuantities());

        // 2) capacitatea în borcane determinată de lăzi
        Map<JarType, Integer> cratesCapacityByJarType = cratesCapacityAsJars(stock);
        log.debug("[planAndPrepareFromOrder] cratesCapacityByJarType(jars)={}", cratesCapacityByJarType);

        // 3) MIN local per JarType
        Map<JarType, Integer> afterDiscreteMin = new EnumMap<>(JarType.class);
        for (Map.Entry<JarType, Integer> e : requestedJars.entrySet()) {
            JarType jt = e.getKey();
            int want = Math.max(0, e.getValue());
            int byJars   = Math.min(want, stock.jarsFor(jt));
            int byLabels = Math.min(want, stock.labelsFor(jt));
            int byCrates = Math.min(want, cratesCapacityByJarType.getOrDefault(jt, 0));
            int localMin = Stream.of(byJars, byLabels, byCrates).min(Integer::compareTo).orElse(0);
            afterDiscreteMin.put(jt, Math.max(0, localMin));

            log.debug("[planAndPrepareFromOrder] jt={}, want={}, byJars={}, byLabels={}, byCrates={}, localMin={}",
                    jt, want, byJars, byLabels, byCrates, localMin);
        }

        // 4) limită GLOBALĂ de miere (kg) pe HoneyType-ul comenzii
        BigDecimal honeyBudgetKg = stock.honeyFor(order.honeyType());
        log.info("[planAndPrepareFromOrder] honeyBudgetKg(type={})={}", order.honeyType(), honeyBudgetKg);

        Map<JarType, Integer> approved = applyHoneyLimitGreedy(afterDiscreteMin, honeyBudgetKg);
        log.debug("[planAndPrepareFromOrder] approvedJars={}", approved);

        // 5) kg de miere folosite efectiv
        BigDecimal honeyUsed = approved.entrySet().stream()
                .map(e -> e.getKey().kgPerJar().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("[planAndPrepareFromOrder] honeyUsedKg={}", honeyUsed);

        // 6) Construiește AllocationPlan (kg de miere), alocă în RDS-ul corect după HoneyType
        Map<HoneyType, BigDecimal> reqKg = (honeyUsed.signum() > 0)
                ? Map.of(order.honeyType(), honeyUsed)
                : Map.of();

        AllocationPlan plan = AllocationPlan.createFromOrder(order, reqKg);
        if (honeyUsed.signum() > 0) {
            String honeyKey = Objects.requireNonNull(
                    honeyWarehouseByType.get(order.honeyType()),
                    "Missing honey warehouse for " + order.honeyType()
            );
            log.info("[planAndPrepareFromOrder] allocate honey to warehouseKey={}", honeyKey);
            plan.allocateToWarehouse(order.honeyType(), honeyUsed, honeyKey, "auto-bottleneck(4-constraints)");
        } else {
            log.warn("[planAndPrepareFromOrder] honeyUsed=0 -> nicio alocare de miere.");
        }

        // 7) Lăzi necesare (ceil)
        Map<CrateType, Integer> crateReservations = computeCrateReservations(approved);
        log.debug("[planAndPrepareFromOrder] crateReservations(crates)={}", crateReservations);

        // 8) PrepCommand către RDS-urile discrete (JARS/LABELS/CRATES)
        List<PrepCommand> cmds = new ArrayList<>();
        for (Map.Entry<JarType, Integer> e : approved.entrySet()) {
            JarType jt = e.getKey(); int qty = e.getValue();
            if (qty <= 0) continue;
            cmds.add(new PrepCommand(jarsWarehouseKey,   PrepCommand.PrepResource.JARS,   jt, qty));
            cmds.add(new PrepCommand(labelsWarehouseKey, PrepCommand.PrepResource.LABELS, jt, qty));
            log.debug("[planAndPrepareFromOrder] +Prep JARS/LABELS jt={}, qty={}", jt, qty);
        }
        for (Map.Entry<CrateType, Integer> e : crateReservations.entrySet()) {
            CrateType ct = e.getKey(); int crates = e.getValue();
            if (crates <= 0) continue;
            JarType jt = ct.getJarType();
            cmds.add(new PrepCommand(cratesWarehouseKey, PrepCommand.PrepResource.CRATES, jt, crates));
            log.debug("[planAndPrepareFromOrder] +Prep CRATES ct={}, jt={}, crates={}", ct, jt, crates);
        }

        log.info("[planAndPrepareFromOrder] commandsToDispatch={}", cmds.size());
        dispatcherOpt.ifPresent(d -> {
            cmds.forEach(c -> {
                log.debug("[planAndPrepareFromOrder] dispatch -> {}", c);
                d.dispatch(c);
            });
        });

        PlanWithPrep result = new PlanWithPrep(plan, approved, honeyUsed, cmds);
        log.info("[planAndPrepareFromOrder] DONE orderNumber={}, status={}, honeyUsedKg={}",
                order.orderNumber(), plan.getStatus(), honeyUsed);
        return result;
    }

    /** Greedy pe limita globală de miere (kg) peste minimele locale. */
    private static Map<JarType, Integer> applyHoneyLimitGreedy(
            Map<JarType, Integer> candidateAfterLocalMin,
            BigDecimal honeyBudgetKg
    ) {
        Map<JarType, Integer> approved = new EnumMap<>(JarType.class);
        BigDecimal remaining = (honeyBudgetKg == null) ? BigDecimal.ZERO : honeyBudgetKg;

        List<JarType> orderList = new ArrayList<>(candidateAfterLocalMin.keySet());
        log.debug("[applyHoneyLimitGreedy] start remainingKg={}, order={}", remaining, orderList);

        for (JarType jt : orderList) {
            int want = Math.max(0, candidateAfterLocalMin.getOrDefault(jt, 0));
            if (want <= 0) {
                approved.put(jt, 0);
                continue;
            }

            BigDecimal kgPerJar = jt.kgPerJar();
            if (kgPerJar.signum() <= 0) {
                log.warn("[applyHoneyLimitGreedy] kgPerJar<=0 for {}", jt);
                approved.put(jt, 0);
                continue;
            }

            int maxByHoney = remaining.divide(kgPerJar, 0, RoundingMode.FLOOR).intValue();
            int grant = Math.min(want, maxByHoney);
            approved.put(jt, grant);

            BigDecimal used = kgPerJar.multiply(BigDecimal.valueOf(grant));
            remaining = remaining.subtract(used);

            log.debug("[applyHoneyLimitGreedy] jt={}, want={}, grant={}, usedKg={}, remainingKg={}",
                    jt, want, grant, used, remaining);

            if (remaining.signum() <= 0) {
                for (JarType rest : candidateAfterLocalMin.keySet()) {
                    approved.putIfAbsent(rest, 0);
                }
                break;
            }
        }
        for (JarType jt : JarType.values()) approved.putIfAbsent(jt, 0);
        log.debug("[applyHoneyLimitGreedy] approved={}", approved);
        return approved;
    }

    /** Transformă aprobările (borcane) în număr de lăzi per CrateType (ceil). */
    private static Map<CrateType, Integer> computeCrateReservations(Map<JarType, Integer> approvedJars) {
        Map<CrateType, Integer> out = new EnumMap<>(CrateType.class);
        for (Map.Entry<JarType, Integer> e : approvedJars.entrySet()) {
            JarType jt = e.getKey();
            int jars = Math.max(0, e.getValue());
            if (jars == 0) continue;

            CrateType ct = switch (jt) {
                case JAR200 -> CrateType.CRATE200;
                case JAR400 -> CrateType.CRATE400;
                case JAR800 -> CrateType.CRATE800;
            };
            int perCrate = ct.getJarsPerCrate();
            int cratesNeeded = (int) Math.ceil(jars / (double) perCrate);
            out.merge(ct, cratesNeeded, Integer::sum);

            log.debug("[computeCrateReservations] jt={}, jars={}, ct={}, perCrate={}, cratesNeeded={}",
                    jt, jars, ct, perCrate, cratesNeeded);
        }
        return out;
    }

    /** Convertește stocul de lăzi (CrateType->count) în capacitate de borcane pe JarType. */
    private static Map<JarType, Integer> cratesCapacityAsJars(PackagingResources stock) {
        Map<JarType, Integer> cap = new EnumMap<>(JarType.class);
        Map<CrateType, Integer> cratesByType = stock.cratesByType(); // vine din RDS

        if (cratesByType != null && !cratesByType.isEmpty()) {
            log.debug("[cratesCapacityAsJars] cratesByType={}", cratesByType);
            for (Map.Entry<CrateType, Integer> e : cratesByType.entrySet()) {
                CrateType ct = e.getKey();
                int cratesCount = Math.max(0, e.getValue());
                int jarsCap = cratesCount * ct.getJarsPerCrate();
                cap.merge(ct.getJarType(), jarsCap, Integer::sum);
            }
            log.debug("[cratesCapacityAsJars] capacityByJarType(jars)={}", cap);
            return cap;
        }

        // fallback (dacă nu e implementat încă)
        for (JarType jt : JarType.values()) {
            int jarsCap = Math.max(0, stock.cratesFor(jt));
            if (jarsCap > 0) cap.put(jt, jarsCap);
        }
        log.debug("[cratesCapacityAsJars] FALLBACK capacityByJarType(jars)={}", cap);
        return cap;
    }

    // ================== STATUS & CACHE ==================

    private void refreshStatus() {
        PlanStatus old = this.status;

        boolean allSatisfied = requested.entrySet().stream()
                .allMatch(e -> getAllocated(e.getKey()).compareTo(e.getValue()) >= 0);
        boolean anyAllocated = allocatedCache.values().stream().anyMatch(v -> v.compareTo(BigDecimal.ZERO) > 0);

        if (status != PlanStatus.REJECTED) {
            if (allSatisfied) status = PlanStatus.FULLY_ALLOCATED;
            else if (anyAllocated) status = PlanStatus.PARTIALLY_ALLOCATED;
            else status = PlanStatus.DRAFT;
        }

        if (old != status) {
            log.info("[refreshStatus] status {} -> {}", old, status);
        } else {
            log.trace("[refreshStatus] status unchanged: {}", status);
        }
    }

    private void recomputeAllocatedCache() {
        allocatedCache.clear();
        for (AllocationLine l : lines) {
            allocatedCache.merge(l.type(), l.quantityKg(), BigDecimal::add);
        }
        for (HoneyType t : HoneyType.values()) {
            allocatedCache.put(t, normalize(allocatedCache.getOrDefault(t, BigDecimal.ZERO)));
        }
        log.debug("[recomputeAllocatedCache] allocatedCache={}", allocatedCache);
    }

    // ================== UTILS ==================

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

    private static BigDecimal normalize(BigDecimal v) { return v.setScale(3, RoundingMode.HALF_UP); }

    private static BigDecimal maxZero(BigDecimal v) {
        return (v == null) ? BigDecimal.ZERO : v.max(BigDecimal.ZERO);
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
                "orderNumber=" + orderNumber +
                ", status=" + status +
                ", requested=" + requested +
                ", allocated=" + allocatedCache +
                ", lines=" + lines +
                '}';
    }

    public static AllocationPlan of(Integer orderNumber,
                                    Map<HoneyType, BigDecimal> requested,
                                    List<AllocationLine> lines) {
        log.debug("[of] orderNumber={}, requested={}, lines#={}", orderNumber, requested, (lines == null ? 0 : lines.size()));
        return new AllocationPlan(
                Objects.requireNonNull(orderNumber, "orderNumber"),
                Instant.now(),
                requested,
                lines,
                PlanStatus.DRAFT
        );
    }

    public static AllocationPlan fromComputedLines(Integer orderNumber,
                                                   Map<HoneyType, BigDecimal> requested,
                                                   List<AllocationLine> lines) {
        log.debug("[fromComputedLines] orderNumber={}, requested={}, lines#={}", orderNumber, requested, (lines == null ? 0 : lines.size()));
        return of(orderNumber, requested, lines);
    }
}
