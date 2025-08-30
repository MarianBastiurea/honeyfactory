package com.marianbastiurea.api.controller;

import com.marianbastiurea.api.dto.request.PlaceOrderRequest;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.services.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService; // service-ul tău de aplicație

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // === Create (DTO in, Domain out) ===
    @PostMapping
    public ResponseEntity<Order> create(@RequestBody PlaceOrderRequest req) {
        Order created = orderService.create(req); // service-ul setează orderNumber
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // === Read by orderNumber ===
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<Order> getByOrderNumber(@PathVariable Integer orderNumber) {
        Optional<Order> found = orderService.findByOrderNumber(orderNumber);
        return found.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // === Query by honey type ===
    @GetMapping("/honey/{honeyType}")
    public ResponseEntity<List<Order>> findByHoneyType(@PathVariable HoneyType honeyType) {
        return ResponseEntity.ok(orderService.findByHoneyType(honeyType));
    }

    // === Delete by orderNumber ===
    @DeleteMapping("/number/{orderNumber}")
    public ResponseEntity<Void> deleteByOrderNumber(@PathVariable Integer orderNumber) {
        orderService.deleteByOrderNumber(orderNumber);
        return ResponseEntity.noContent().build();
    }

}
