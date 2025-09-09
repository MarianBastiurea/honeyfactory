package com.marianbastiurea.infrastructure.gateway;

import com.marianbastiurea.api.dto.ReserveLineResult;
import com.marianbastiurea.api.dto.ReserveResult;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.model.AllocationLine;
import com.marianbastiurea.domain.model.AllocationPlan;
import com.marianbastiurea.domain.repo.HoneyRepo;
import com.marianbastiurea.domain.services.ReservationOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.StructuredTaskScope;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.util.*;

@Component
public class SimpleWarehouseStockGateway implements ReservationOrchestrator.WarehouseStockGateway {

    private static final Logger log = LoggerFactory.getLogger(SimpleWarehouseStockGateway.class);

    private final HoneyRepo honey;
    public SimpleWarehouseStockGateway(@Qualifier("routerHoneyRepo") HoneyRepo honey) {
        this.honey = honey;
    }

    @Override
    public LinkedHashMap<String, Map<HoneyType, BigDecimal>> loadAvailable() {
        EnumMap<HoneyType, BigDecimal> stock = new EnumMap<>(HoneyType.class);
        for (HoneyType t : HoneyType.values()) {
            try {
                stock.put(t, honey.freeKg(t)); // citește stocul liber per tip
            } catch (Exception e) {
                log.warn("Nu am putut obține stocul pentru {}: {}", t, e.toString());
                stock.put(t, BigDecimal.ZERO);
            }
        }
        LinkedHashMap<String, Map<HoneyType, BigDecimal>> out = new LinkedHashMap<>();
        out.put("MAIN", stock); // o singură “magazie” pentru început
        return out;
    }

    @Override
    public void reserve(String warehouseKey, HoneyType type, BigDecimal quantityKg) {
        int technicalOrderNo = -1; // dacă nu ai orderNumber la îndemână aici
        var res = honey.processOrder(type, technicalOrderNo, quantityKg);
        if (res.deliveredKg().compareTo(quantityKg) < 0) {
            throw new IllegalStateException(
                    "Stoc insuficient pentru %s (cerut %s, livrat %s)"
                            .formatted(type, quantityKg, res.deliveredKg()));
        }
        log.info("Reserved {} kg of {} from {}. newStock={}, v={}",
                res.deliveredKg(), type, warehouseKey, res.newStock(), res.newVersion());
    }

    @Override
    public ReserveResult reserve(AllocationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        int orderNumber = plan.getOrderNumber();

        // Colectăm liniile cu cantități > 0
        List<AllocationLine> lines = plan.getLines().stream()
                .filter(l -> l.quantityKg() != null && l.quantityKg().signum() > 0)
                .toList();

        if (lines.isEmpty()) {
            return new ReserveResult(orderNumber, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, true);
        }

        // NOTĂ: pe JDK 21/23, constructorul fără parametri pornește subtask-urile ca virtual threads.
        // Dacă build-ul tău suportă, poți folosi și varianta (comentată) cu factory explicit:
        // try (var scope = new StructuredTaskScope.ShutdownOnFailure(Thread.ofVirtual().factory())) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            List<StructuredTaskScope.Subtask<ReserveLineResult>> tasks = new ArrayList<>(lines.size());

            for (AllocationLine line : lines) {
                tasks.add(scope.fork(() -> {
                    BigDecimal requested = line.quantityKg();
                    var dr = honey.processOrder(line.type(), orderNumber, requested);

                    log.info("[reserve:line] order={}, wh={}, type={}, reqKg={}, delKg={}, newStock={}, v={}",
                            orderNumber, line.warehouseKey(), line.type(),
                            requested, dr.deliveredKg(), dr.newStock(), dr.newVersion());

                    return new ReserveLineResult(
                            line,
                            requested,
                            dr.deliveredKg(),
                            dr.newStock(),
                            dr.newVersion()
                    );
                }));
            }

            scope.join(); // așteaptă toate
            scope.throwIfFailed(ex -> new IllegalStateException(
                    "Honey reservation failed for order " + orderNumber, ex));

            List<ReserveLineResult> results = tasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();

            BigDecimal totalReq = results.stream()
                    .map(ReserveLineResult::requestedKg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalDel = results.stream()
                    .map(ReserveLineResult::deliveredKg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            boolean fully = totalReq.compareTo(totalDel) == 0;

            if (!fully) {
                log.warn("[reserve:summary] PARTIAL delivery: order={}, requestedKg={}, deliveredKg={}",
                        orderNumber, totalReq, totalDel);
            } else {
                log.info("[reserve:summary] FULL delivery: order={}, totalKg={}", orderNumber, totalDel);
            }

            return new ReserveResult(orderNumber, results, totalReq, totalDel, fully);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Reservation interrupted (order " + orderNumber + ")", ie);
        }
    }
}
