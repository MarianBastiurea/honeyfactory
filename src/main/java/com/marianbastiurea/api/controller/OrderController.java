package com.marianbastiurea.api.controller;

import com.marianbastiurea.api.dto.PlaceOrderRequest;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.services.OrderService;
import com.marianbastiurea.domain.services.StockSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final StockSnapshotService snapshot;

    public OrderController(OrderService orderService, StockSnapshotService snapshot) {
        this.orderService = orderService;
        this.snapshot = snapshot;
    }

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody PlaceOrderRequest req) {
        log.info("order.create.request honeyType={} jarQuantities={}", req.honeyType(), req.jarQuantities());
        try {
            Order created = orderService.create(req);
            MDC.put("orderNumber", String.valueOf(created.orderNumber()));
            log.info("order.create.success");
            if (log.isDebugEnabled()) {
                log.debug("order.create.payload result={}", created);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("order.create.failure", e);
            throw e;
        } finally {
            MDC.remove("orderNumber");
        }
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<Order> getByOrderNumber(@PathVariable Integer orderNumber) {
        MDC.put("orderNumber", String.valueOf(orderNumber));
        log.info("order.get.request");
        try {
            Optional<Order> found = orderService.findByOrderNumber(orderNumber);
            if (found.isPresent()) {
                log.info("order.get.found");
                if (log.isDebugEnabled()) log.debug("order.get.payload result={}", found.get());
                return ResponseEntity.ok(found.get());
            } else {
                log.info("order.get.not_found");
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("order.get.failure", e);
            throw e;
        } finally {
            MDC.remove("orderNumber");
        }
    }


    @GetMapping("/honey/{honeyType}")
    public ResponseEntity<List<Order>> findByHoneyType(@PathVariable HoneyType honeyType) {
        log.info("order.search_by_honey.request honeyType={}", honeyType);
        try {
            List<Order> result = orderService.findByHoneyType(honeyType);
            log.info("order.search_by_honey.success size={}", result.size());
            if (log.isDebugEnabled()) log.debug("order.search_by_honey.payload result={}", result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("order.search_by_honey.failure honeyType={}", honeyType, e);
            throw e;
        }
    }


    @DeleteMapping("/number/{orderNumber}")
    public ResponseEntity<Void> deleteByOrderNumber(@PathVariable Integer orderNumber) {
        MDC.put("orderNumber", String.valueOf(orderNumber));
        log.info("order.delete.request");
        try {
            orderService.deleteByOrderNumber(orderNumber);
            log.info("order.delete.success");
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("order.delete.failure", e);
            throw e;
        } finally {
            MDC.remove("orderNumber");
        }
    }

    @PostMapping("/check")
    public ResponseEntity<BigDecimal> check(@RequestBody Order order) throws Exception {

        return ResponseEntity.ok(snapshot.computeAllocatableKg(order));
    }

    @GetMapping("/number/{orderNumber}/honey/{honeyType}")
    public ResponseEntity<Order> getByNumberAndHoney(@PathVariable Integer orderNumber,
                                                     @PathVariable HoneyType honeyType) {
        return orderService.findByOrderNumberAndHoneyType(orderNumber, honeyType)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
