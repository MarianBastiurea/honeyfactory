package com.marianbastiurea.domain.services;

import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.repo.CrateRepo;
import com.marianbastiurea.domain.repo.HoneyRepo;
import com.marianbastiurea.domain.repo.JarRepo;
import com.marianbastiurea.domain.repo.LabelRepo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;



@Service
public class StockSnapshotService {

    private final HoneyRepo honey;
    private final JarRepo jars;
    private final LabelRepo labels;
    private final CrateRepo crates;
    private final ThreadFactory tf; // VT în producție

    public StockSnapshotService(HoneyRepo honey,
                                JarRepo jars,
                                LabelRepo labels,
                                CrateRepo crates,
                                @Qualifier("taskThreadFactory") ThreadFactory taskThreadFactory) {
        this.honey = honey;
        this.jars = jars;
        this.labels = labels;
        this.crates = crates;
        this.tf = taskThreadFactory;
    }

    /** Întoarce kg alocabile limitate de {miere, borcane, etichete, lăzi}. */
    public BigDecimal computeAllocatableKg(Order order) throws Exception {
        try (var scope = new StructuredTaskScope<BigDecimal>("alloc-scope", tf)) {
            var tHoney  = scope.fork(() -> honey.freeKg(order.honeyType()));
            var tJars   = scope.fork(() -> jars.freeAsKg(order.jarQuantities(), order.honeyType()));
            var tLabels = scope.fork(() -> labels.freeAsKg(order.jarQuantities(), order.honeyType()));
            var tCrates = scope.fork(() -> crates.freeAsKg(order.jarQuantities(), order.honeyType()));

            scope.join(); // așteaptă toate

            // Dacă vreun task a aruncat, get() va arunca ExecutionException.
            return min(tHoney.get(), tJars.get(), tLabels.get(), tCrates.get());
        }
    }

    private static BigDecimal min(BigDecimal... v) {
        BigDecimal m = v[0];
        for (int i = 1; i < v.length; i++) if (v[i].compareTo(m) < 0) m = v[i];
        return m.max(BigDecimal.ZERO);
    }
}