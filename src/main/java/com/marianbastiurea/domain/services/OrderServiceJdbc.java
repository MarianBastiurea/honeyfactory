package com.marianbastiurea.domain.services;

import com.marianbastiurea.api.dto.PlaceOrderRequest;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.repo.OrderRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@Service
public class OrderServiceJdbc implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceJdbc.class);

    private final OrderRepo repo;

    public OrderServiceJdbc(OrderRepo repo) {
        this.repo = requireNonNull(repo, "repo");
    }

    @Override
    @Transactional
    public Order create(PlaceOrderRequest req) {
        requireNonNull(req, "req");

        Integer orderNo = req.orderNumber();
        HoneyType honey = req.honeyType();
        Map<?, Integer> jars = req.jarQuantities();

        int jarTypes = jars != null ? jars.size() : 0;
        int totalJars = totalJars(jars);

        log.info("Create order: orderNumber={}, honeyType={}, jarTypes={}, totalJars={}",
                orderNo, honey, jarTypes, totalJars);

        long t0 = System.nanoTime();
        try {
            repo.upsertOrderLines(orderNo, honey, req.jarQuantities());
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("Order upserted successfully in {} ms: #{} [{}]", tookMs, orderNo, honey);
            return new Order(honey, req.jarQuantities(), orderNo);
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("Create order FAILED in {} ms: orderNumber={}, honeyType={}, cause={}",
                    tookMs, orderNo, honey, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumber(Integer orderNumber) {
        long t0 = System.nanoTime();
        try {
            Optional<Order> res = repo.findByOrderNumber(orderNumber);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.debug("findByOrderNumber({}) -> {} ({} ms)",
                    orderNumber, res.isPresent() ? "FOUND" : "NOT FOUND", tookMs);
            return res;
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("findByOrderNumber({}) FAILED in {} ms: {}", orderNumber, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumberAndHoneyType(Integer orderNumber, HoneyType honeyType) {
        long t0 = System.nanoTime();
        try {
            Optional<Order> res = repo.findByOrderNumberAndHoneyType(orderNumber, honeyType);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.debug("findByOrderNumberAndHoneyType({}, {}) -> {} ({} ms)",
                    orderNumber, honeyType, res.isPresent() ? "FOUND" : "NOT FOUND", tookMs);
            return res;
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("findByOrderNumberAndHoneyType({}, {}) FAILED in {} ms: {}",
                    orderNumber, honeyType, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByHoneyType(HoneyType honeyType) {
        long t0 = System.nanoTime();
        try {
            List<Order> res = repo.findByHoneyType(honeyType);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.debug("findByHoneyType({}) -> {} item(s) ({} ms)", honeyType, res.size(), tookMs);
            return res;
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("findByHoneyType({}) FAILED in {} ms: {}", honeyType, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    @Transactional
    public void deleteByOrderNumber(Integer orderNumber) {
        log.warn("Deleting order by orderNumber={}", orderNumber);
        long t0 = System.nanoTime();
        try {
            repo.deleteByOrderNumber(orderNumber);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("deleteByOrderNumber({}) -> OK ({} ms)", orderNumber, tookMs);
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("deleteByOrderNumber({}) FAILED in {} ms: {}", orderNumber, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    // ---- helpers ----
    private static int totalJars(Map<?, Integer> jars) {
        if (jars == null || jars.isEmpty()) return 0;
        return jars.values().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }
}
