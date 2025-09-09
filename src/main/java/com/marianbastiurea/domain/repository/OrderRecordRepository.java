package com.marianbastiurea.domain.repository;

import com.marianbastiurea.domain.model.OrderRecord;

import java.util.List;
import java.util.Optional;

public interface OrderRecordRepository {
    OrderRecord save(OrderRecord order);
    Optional<OrderRecord> findById(String id);
    List<OrderRecord> findByStatus(String status);
    void deleteById(String id);
}

