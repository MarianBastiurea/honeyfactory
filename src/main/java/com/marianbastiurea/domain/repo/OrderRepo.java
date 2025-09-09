package com.marianbastiurea.domain.repo;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OrderRepo {
    void upsertOrderLines(Integer orderNumber, HoneyType honeyType, Map<JarType, Integer> jarQuantities);

    Optional<Order> findByOrderNumber(Integer orderNumber);
    Optional<Order> findByOrderNumberAndHoneyType(Integer orderNumber, HoneyType honeyType);
    List<Order> findByHoneyType(HoneyType honeyType);

    List<Order> loadPendingGrouped();              // (order_number, honey_type) => Order
    boolean tryMarkProcessing(int orderNumber, HoneyType type);
    void markCompleted(int orderNumber, HoneyType type, BigDecimal deliveredKg);
    void markFailed(int orderNumber, HoneyType type, String reason);

    void deleteByOrderNumber(Integer orderNumber);
}
