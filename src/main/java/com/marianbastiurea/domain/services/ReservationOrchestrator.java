package com.marianbastiurea.domain.services;

import com.marianbastiurea.domain.enums.CrateType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.enums.LabelType;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.model.PackagingSnapshot;
import com.marianbastiurea.domain.model.StockRow;
import com.marianbastiurea.domain.repo.CrateRepo;
import com.marianbastiurea.domain.repo.HoneyRepo;
import com.marianbastiurea.domain.repo.JarRepo;
import com.marianbastiurea.domain.repo.LabelRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;

import static java.util.Objects.requireNonNull;

@Service
public class ReservationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReservationOrchestrator.class);

    private final HoneyRepo honeyRepo;
    private final JarRepo jarRepo;
    private final CrateRepo crateRepo;
    private final LabelRepo labelRepo;

    private final TransactionTemplate jarsTT;
    private final TransactionTemplate cratesTT;
    private final TransactionTemplate labelsTT;

    private final NamedParameterJdbcTemplate jarsTpl;
    private final NamedParameterJdbcTemplate labelsTpl;
    private final NamedParameterJdbcTemplate cratesTpl;

    private final ThreadFactory vtFactory;

    public ReservationOrchestrator(@Qualifier("routerHoneyRepo") HoneyRepo honeyRepo,
                                   JarRepo jarRepo,
                                   CrateRepo crateRepo,
                                   LabelRepo labelRepo,
                                   @Qualifier("jarsTT") TransactionTemplate jarsTT,
                                   @Qualifier("cratesTT") TransactionTemplate cratesTT,
                                   @Qualifier("labelsTT") TransactionTemplate labelsTT,
                                   @Qualifier("jarsTpl") NamedParameterJdbcTemplate jarsTpl,
                                   @Qualifier("labelsTpl") NamedParameterJdbcTemplate labelsTpl,
                                   @Qualifier("cratesTpl") NamedParameterJdbcTemplate cratesTpl,
                                   @Qualifier("vtThreadFactory") ThreadFactory vtFactory) {
        this.honeyRepo = requireNonNull(honeyRepo, "honeyRepo");
        this.jarRepo = requireNonNull(jarRepo, "jarRepo");
        this.crateRepo = requireNonNull(crateRepo, "crateRepo");
        this.labelRepo = requireNonNull(labelRepo, "labelRepo");
        this.jarsTT = requireNonNull(jarsTT, "jarsTT");
        this.cratesTT = requireNonNull(cratesTT, "cratesTT");
        this.labelsTT = requireNonNull(labelsTT, "labelsTT");
        this.jarsTpl = requireNonNull(jarsTpl, "jarsTpl");
        this.labelsTpl = requireNonNull(labelsTpl, "labelsTpl");
        this.cratesTpl = requireNonNull(cratesTpl, "cratesTpl");
        this.vtFactory = requireNonNull(vtFactory, "vtFactory");
    }

    public ReservationResult reserveFor(Order order) {
        requireNonNull(order, "order");
        if (order.jarQuantities() == null || order.jarQuantities().isEmpty()) {
            return ReservationResult.failure("Nu s-au cerut borcane pentru comanda #" + order.orderNumber());
        }

        long t0 = System.nanoTime();
        try {
            PackagingSnapshot snapshot;
            BigDecimal honeyFreeKg;
            try (var scope = new StructuredTaskScope.ShutdownOnFailure("load-inputs", vtFactory)) {
                var fSnap = scope.fork(() -> loadPackagingSnapshotFor(order));
                var fHoney = scope.fork(() -> nonNeg(honeyRepo.availableKg(order.honeyType())));
                scope.join().throwIfFailed();
                snapshot = fSnap.get();
                honeyFreeKg = fHoney.get();
            }

            BigDecimal needKg = jarsToKg(order.jarQuantities());
            if (needKg.signum() <= 0) return ReservationResult.failure("No quantity.");

            Caps caps = capsFromSnapshot(order, snapshot);
            BigDecimal pkgCapKg = min(caps.jarsKg, caps.labelsKg, caps.cratesKg);

            BigDecimal targetKg = min(needKg, honeyFreeKg, pkgCapKg);
            if (targetKg.signum() <= 0) {
                return ReservationResult.failure("Can't deliver nothing: need=" + needKg + ", honey=" + honeyFreeKg + ", pkg=" + pkgCapKg);
            }

            Map<JarType, Integer> approvedJars = reduceJarsToTargetKg(order.jarQuantities(), targetKg);
            BigDecimal approvedKg = jarsToKg(approvedJars);
            if (approvedKg.signum() <= 0 || isZeroJars(approvedJars)) {
                return ReservationResult.failure("No jars delivered (targetKg=" + targetKg + ").");
            }

            var honeyRes = honeyRepo.processOrder(order.honeyType(), order.orderNumber(), approvedKg);
            BigDecimal deliveredKg = nonNeg(honeyRes.deliveredKg());
            if (deliveredKg.signum() <= 0) {
                return ReservationResult.failure("No honey delivered (deliver=0).");
            }


            Map<JarType, Integer> planForDelivered = reduceJarsToTargetKg(approvedJars, deliveredKg);
            BigDecimal packagingKg = jarsToKg(planForDelivered);
            log.info("[deliver] PACKAGING PLAN (from honeyDelivered={}):\n{}", deliveredKg, fmtJarBreakdown(planForDelivered));


            jarsTT.execute(s -> {
                jarRepo.deliveredJars(planForDelivered);
                return null;
            });
            labelsTT.execute(s -> {
                labelRepo.deliveredLabels(planForDelivered);
                return null;
            });
            cratesTT.execute(s -> {
                crateRepo.deliveredCrates(planForDelivered);
                return null;
            });

            long ms = (System.nanoTime() - t0) / 1_000_000;
            int totalJarsDelivered = planForDelivered.values().stream().mapToInt(Integer::intValue).sum();
            log.info("[deliver] ✅ SUCCESS order#{} [{}]: deliveredKg={}, jars={}, {} ms",
                    order.orderNumber(), order.honeyType(), deliveredKg, totalJarsDelivered, ms);

            return ReservationResult.success("Delivered " + deliveredKg + " kg (" + totalJarsDelivered + " borcane).");

        } catch (Exception ex) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.error("[deliver] ❌ ERROR order#{} [{}] in {} ms. jars:\n{}",
                    order.orderNumber(), order.honeyType(), ms, fmtJarBreakdown(order.jarQuantities()), ex);
            return ReservationResult.failure("Error: " + ex.getMessage());
        }
    }


    private PackagingSnapshot loadPackagingSnapshotFor(Order order) throws Exception {
        Set<JarType> jarTypes = order.jarQuantities().keySet();
        Set<LabelType> labelTypes = new HashSet<>();
        Set<CrateType> crateTypes = new HashSet<>();
        for (JarType jt : jarTypes) {
            labelTypes.add(labelTypeFor(jt));
            crateTypes.add(CrateType.forJarType(jt));
        }

        try (var scope = new StructuredTaskScope.ShutdownOnFailure("snap", vtFactory)) {
            var fJars = scope.fork(() -> loadJarsStock(jarTypes));
            var fLabels = scope.fork(() -> loadLabelsStock(labelTypes));
            var fCrates = scope.fork(() -> loadCratesStock(crateTypes));
            scope.join().throwIfFailed();
            return new PackagingSnapshot(fJars.get(), fLabels.get(), fCrates.get());
        }
    }

    private Map<JarType, StockRow> loadJarsStock(Set<JarType> types) {
        if (types.isEmpty()) return Map.of();
        String sql = """
                    SELECT jar_type, COALESCE(final_stock,0) AS final_stock, row_version
                    FROM public.jar_stock
                    WHERE jar_type IN (:types)
                """;
        List<String> names = types.stream().map(Enum::name).toList();
        return jarsTpl.query(sql, Map.of("types", names), rs -> {
            EnumMap<JarType, StockRow> m = new EnumMap<>(JarType.class);
            while (rs.next()) {
                JarType jt = JarType.valueOf(rs.getString("jar_type"));
                m.put(jt, new StockRow(rs.getLong("row_version"), Math.max(rs.getInt("final_stock"), 0)));
            }
            return m;
        });
    }

    private Map<LabelType, StockRow> loadLabelsStock(Set<LabelType> types) {
        if (types.isEmpty()) return Map.of();
        String sql = """
                    SELECT label_type, COALESCE(final_stock,0) AS final_stock, row_version
                    FROM public.label_stock
                    WHERE label_type IN (:types)
                """;
        List<String> names = types.stream().map(Enum::name).toList();
        return labelsTpl.query(sql, Map.of("types", names), rs -> {
            EnumMap<LabelType, StockRow> m = new EnumMap<>(LabelType.class);
            while (rs.next()) {
                LabelType lt = LabelType.valueOf(rs.getString("label_type"));
                m.put(lt, new StockRow(rs.getLong("row_version"), Math.max(rs.getInt("final_stock"), 0)));
            }
            return m;
        });
    }

    private Map<CrateType, StockRow> loadCratesStock(Set<CrateType> types) {
        if (types.isEmpty()) return Map.of();
        String sql = """
                    SELECT crate_type, COALESCE(final_stock,0) AS final_stock, row_version
                    FROM public.crate_stock
                    WHERE crate_type IN (:types)
                """;
        List<String> names = types.stream().map(Enum::name).toList();
        return cratesTpl.query(sql, Map.of("types", names), rs -> {
            EnumMap<CrateType, StockRow> m = new EnumMap<>(CrateType.class);
            while (rs.next()) {
                CrateType ct = CrateType.valueOf(rs.getString("crate_type"));
                m.put(ct, new StockRow(rs.getLong("row_version"), Math.max(rs.getInt("final_stock"), 0)));
            }
            return m;
        });
    }


    private record Caps(BigDecimal jarsKg, BigDecimal labelsKg, BigDecimal cratesKg) {
    }

    private Caps capsFromSnapshot(Order order, PackagingSnapshot snap) {
        Map<JarType, Integer> req = order.jarQuantities();

        BigDecimal jarsKg = BigDecimal.ZERO;
        BigDecimal labelsKg = BigDecimal.ZERO;
        BigDecimal cratesKg = BigDecimal.ZERO;

        for (var e : req.entrySet()) {
            JarType jt = e.getKey();
            int q = Math.max(0, e.getValue() == null ? 0 : e.getValue());

            int jarsAvail = getFinal(snap.jars().get(jt));
            int jarsCan = Math.min(q, jarsAvail);
            jarsKg = jarsKg.add(jt.kgPerJar().multiply(BigDecimal.valueOf(jarsCan)));

            LabelType lt = labelTypeFor(jt);
            int labelsAvail = getFinal(snap.labels().get(lt));
            int labelsCan = Math.min(q, labelsAvail);
            labelsKg = labelsKg.add(jt.kgPerJar().multiply(BigDecimal.valueOf(labelsCan)));

            CrateType ct = CrateType.forJarType(jt);
            int cratesAvail = getFinal(snap.crates().get(ct));
            int jarsSupportedByCrates = ct.jarsCapacityForCrates(cratesAvail);
            int canFillJarsWithCrates = Math.min(q, jarsSupportedByCrates);
            cratesKg = cratesKg.add(jt.kgPerJar().multiply(BigDecimal.valueOf(canFillJarsWithCrates)));
        }
        return new Caps(jarsKg, labelsKg, cratesKg);
    }

    private static int getFinal(StockRow r) {
        return r == null ? 0 : Math.max(0, r.finalStock());
    }

    private static LabelType labelTypeFor(JarType jt) {
        return switch (jt) {
            case JAR200 -> LabelType.LABEL200;
            case JAR400 -> LabelType.LABEL400;
            case JAR800 -> LabelType.LABEL800;
        };
    }


    public record ReservationResult(boolean success, String message) {
        public static ReservationResult success(String m) {
            return new ReservationResult(true, m);
        }

        public static ReservationResult failure(String m) {
            return new ReservationResult(false, m);
        }
    }

    private static BigDecimal nonNeg(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.max(BigDecimal.ZERO);
    }

    private static BigDecimal min(BigDecimal... vs) {
        BigDecimal m = vs[0];
        for (int i = 1; i < vs.length; i++) if (vs[i].compareTo(m) < 0) m = vs[i];
        return m;
    }

    private static BigDecimal jarsToKg(Map<JarType, Integer> jars) {
        if (jars == null || jars.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (var e : jars.entrySet()) {
            int q = Math.max(0, e.getValue() == null ? 0 : e.getValue());
            if (q == 0) continue;
            sum = sum.add(e.getKey().kgPerJar().multiply(BigDecimal.valueOf(q)));
        }
        return sum;
    }

    private static Map<JarType, Integer> reduceJarsToTargetKg(Map<JarType, Integer> requested, BigDecimal targetKg) {
        if (requested == null || requested.isEmpty() || targetKg == null || targetKg.signum() <= 0) return Map.of();

        BigDecimal needKg = jarsToKg(requested);
        if (needKg.signum() <= 0) return Map.of();

        BigDecimal ratio = targetKg.divide(needKg, 12, RoundingMode.DOWN);
        EnumMap<JarType, Integer> reduced = new EnumMap<>(JarType.class);
        for (var e : requested.entrySet()) {
            JarType jt = e.getKey();
            int q = Math.max(0, e.getValue() == null ? 0 : e.getValue());
            int scaled = ratio.signum() > 0
                    ? new BigDecimal(q).multiply(ratio).setScale(0, RoundingMode.FLOOR).intValue()
                    : 0;
            reduced.put(jt, Math.min(scaled, q));
        }

        BigDecimal used = jarsToKg(reduced);
        outer:
        while (used.compareTo(targetKg) < 0) {
            boolean progressed = false;
            for (JarType jt : JarType.values()) {
                int have = reduced.getOrDefault(jt, 0);
                int maxAllowed = Math.max(0, requested.getOrDefault(jt, 0));
                if (have >= maxAllowed) continue;
                BigDecimal after = used.add(jt.kgPerJar());
                if (after.compareTo(targetKg) <= 0) {
                    reduced.put(jt, have + 1);
                    used = after;
                    progressed = true;
                    if (used.compareTo(targetKg) >= 0) break outer;
                }
            }
            if (!progressed) break;
        }
        return reduced;
    }

    private static boolean isZeroJars(Map<JarType, Integer> m) {
        if (m == null || m.isEmpty()) return true;
        return m.values().stream().mapToInt(v -> v == null ? 0 : v).sum() == 0;
    }

    private static String fmtJarBreakdown(Map<JarType, Integer> m) {
        if (m == null || m.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        int total = 0;
        BigDecimal totalKg = BigDecimal.ZERO;
        for (JarType jt : JarType.values()) {
            int q = m.getOrDefault(jt, 0);
            if (q <= 0) continue;
            BigDecimal kg = jt.kgPerJar().multiply(BigDecimal.valueOf(q));
            sb.append(String.format("  - %-6s : qty=%-5d  kgPerJar=%-4s  kg=%s%n",
                    jt.name(), q, jt.kgPerJar().toPlainString(), kg.toPlainString()));
            total += q;
            totalKg = totalKg.add(kg);
        }
        sb.append(String.format("  Σ jars=%d  Σ kg=%s", total, totalKg.toPlainString()));
        return sb.toString();
    }
}
