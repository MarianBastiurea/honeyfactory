package com.marianbastiurea.api.controller;


import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.services.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;


    public OrderController(OrderService orderService) {
        this.orderService = orderService;

    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<List<Order>> getByOrderNumber(@PathVariable Integer orderNumber) {
        MDC.put("orderNumber", String.valueOf(orderNumber));
        log.info("order.get.request");
        long t0 = System.nanoTime();
        try {
            List<Order> found = orderService.findByOrderNumber(orderNumber);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;

            if (found == null || found.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            log.error("order.get.failure", e);
            throw e;
        } finally {
            MDC.remove("orderNumber");
        }
    }
}
