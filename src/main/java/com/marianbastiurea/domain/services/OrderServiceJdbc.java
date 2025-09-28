package com.marianbastiurea.domain.services;

import com.marianbastiurea.api.dto.PlaceOrderRequest;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.repo.OrderRepo;
import com.marianbastiurea.domain.repository.OrderRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@Service
public class OrderServiceJdbc implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceJdbc.class);
    private final OrderRepo repo;
    private final OrderRecordRepository orderRecordRepository;

    public OrderServiceJdbc(OrderRepo repo, OrderRecordRepository orderRecordRepository) {
        this.repo = requireNonNull(repo, "repo");
        this.orderRecordRepository = orderRecordRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByOrderNumber(Integer orderNumber) {
        Objects.requireNonNull(orderNumber, "orderNumber");
        try {
            return repo.findByOrderNumber(orderNumber);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to load orders for orderNumber=" + orderNumber, ex);
        }
    }


    @Override
    @Transactional
    public void logProcessing(int orderNumber,
                              Map<JarType, Integer> requestedByJar,
                              Map<JarType, Integer> deliveredByJar,
                              String reason) {
        requireNonNull(requestedByJar, "requestedByJar");
        requireNonNull(deliveredByJar, "deliveredByJar");

        final String why = (reason == null) ? "" : reason;

        requestedByJar.forEach((jt, req) -> {
            int requested = (req == null) ? 0 : req;
            int delivered = deliveredByJar.getOrDefault(jt, 0);
            repo.insertProcessingLog(orderNumber, jt, requested, delivered, why);
        });
    }


    @Transactional
    @Override
    public Order create(PlaceOrderRequest req) {
        requireNonNull(req, "req");
        Order order = new Order(req.honeyType(), req.jarQuantities(), req.orderNumber());
        orderRecordRepository.save(order);
        return order;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
