package com.marianbastiurea.domain.services;

import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.repo.CrateRepo;
import com.marianbastiurea.domain.repo.HoneyRepo;
import com.marianbastiurea.domain.repo.JarRepo;
import com.marianbastiurea.domain.repo.LabelRepo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.Future;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;

@Service
public class StockSnapshotService {

    private final HoneyRepo honeyRepo;
    private final JarRepo jarRepo;
    private final LabelRepo labelRepo;
    private final CrateRepo crateRepo;
    private final ThreadFactory taskThreadFactory; // VT in producție, PT la test dacă setezi proprietatea

    public StockSnapshotService(HoneyRepo honeyRepo,
                                JarRepo jarRepo,
                                LabelRepo labelRepo,
                                CrateRepo crateRepo,
                                @Qualifier("taskThreadFactory") ThreadFactory taskThreadFactory) {
        this.honeyRepo = honeyRepo;
        this.jarRepo = jarRepo;
        this.labelRepo = labelRepo;
        this.crateRepo = crateRepo;
        this.taskThreadFactory = taskThreadFactory;
    }

    public BigDecimal computeAllocatableKg(Order order) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure(taskThreadFactory)) {
            Future<BigDecimal> honey  = scope.fork(() -> honeyRepo.freeKg(order.honeyType()));
            Future<BigDecimal> jars   = scope.fork(() -> jarRepo.freeAsKg(order.jarQuantities(), order.honeyType()));
            Future<BigDecimal> labels = scope.fork(() -> labelRepo.freeAsKg(order.jarQuantities(), order.honeyType()));
            Future<BigDecimal> crates = scope.fork(() -> crateRepo.freeAsKg(order.jarQuantities(), order.honeyType()));

            scope.join().throwIfFailed();
            return min(honey.resultNow(), jars.resultNow(), labels.resultNow(), crates.resultNow());
        }
    }

    private static BigDecimal min(BigDecimal... v) {
        BigDecimal m = v[0];
        for (int i = 1; i < v.length; i++) if (v[i].compareTo(m) < 0) m = v[i];
        return m.max(BigDecimal.ZERO);
    }
}

