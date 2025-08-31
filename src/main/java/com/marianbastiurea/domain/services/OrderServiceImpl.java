package com.marianbastiurea.domain.services;

import com.marianbastiurea.api.dto.request.PlaceOrderRequest;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.repository.OrderJpaRepository;
import com.marianbastiurea.persistence.sql.OrderEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Service
public class OrderServiceImpl implements OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private final OrderJpaRepository repo;

    public OrderServiceImpl(OrderJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public Order create(PlaceOrderRequest req) {
        requireNonNull(req, "PlaceOrderRequest must not be null");
        var honeyType   = requireNonNull(req.honeyType(), "honeyType is required");
        var orderNumber = requireNonNull(req.orderNumber(), "orderNumber is required");

        Map<JarType, Integer> jars = requireNonNull(req.jarQuantities(), "jarQuantities is required")
                .entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null && e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (jars.isEmpty()) {
            throw new IllegalArgumentException("jarQuantities must contain at least one positive entry");
        }

        MDC.put("orderNumber", String.valueOf(orderNumber));
        log.info("order.create.request honeyType={} jars={}", honeyType, jars);

        OrderEntity entity = OrderEntity.of(orderNumber, honeyType, jars);
        entity.setOrderNumber(orderNumber);
        entity.setHoneyType(honeyType);
        entity.setJarQuantities(jars);

        try {
            OrderEntity saved = repo.save(entity);
            log.info("order.persist.ok");
            Order out = saved.toDomain();
            if (log.isDebugEnabled()) log.debug("order.create.result {}", out);
            return out;
        } catch (DataIntegrityViolationException dup) {
            log.error("order.persist.duplicate_key orderNumber already exists");
            throw dup; // sau aruncă o excepție custom (Conflict)
        } finally {
            MDC.remove("orderNumber");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumber(Integer orderNumber) {
        requireNonNull(orderNumber, "orderNumber is required");
        MDC.put("orderNumber", String.valueOf(orderNumber));
        try {
            log.info("order.fetch.start");
            return repo.findById(orderNumber).map(OrderEntity::toDomain);
        } finally {
            log.info("order.fetch.done");
            MDC.remove("orderNumber");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByHoneyType(HoneyType honeyType) {
        requireNonNull(honeyType, "honeyType is required");
        log.info("order.search_by_honey.start honeyType={}", honeyType);
        var list = repo.findByHoneyType(honeyType).stream().map(OrderEntity::toDomain).toList();
        log.info("order.search_by_honey.done size={}", list.size());
        return list;
    }

    @Override
    @Transactional
    public void deleteByOrderNumber(Integer orderNumber) {
        requireNonNull(orderNumber, "orderNumber is required");
        MDC.put("orderNumber", String.valueOf(orderNumber));
        try {
            log.info("order.delete.start");
            if (repo.existsById(orderNumber)) {
                repo.deleteById(orderNumber);
                log.info("order.delete.success");
            } else {
                log.info("order.delete.not_found");
            }
        } finally {
            MDC.remove("orderNumber");
        }
    }
}
