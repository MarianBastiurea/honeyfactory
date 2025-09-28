package com.marianbastiurea.domain.repo;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;

import java.util.List;

public interface OrderRepo {
    List<Order> findByOrderNumber(Integer orderNumber);


    void logProcessingBatch(int orderNumber, List<ProcessingLogRow> rows);


    default void insertProcessingLog(int orderNumber, HoneyType ht, JarType jt, int req, int del, String reason) {
        logProcessingBatch(orderNumber, List.of(new ProcessingLogRow(ht, jt, req, del, reason)));
    }

    void insertProcessingLog(int orderNumber, JarType jt, int req, int del, String reason);

    record ProcessingLogRow(HoneyType honeyType, JarType jarType, int requestedQty, int deliveredQty, String reason) {
    }
}
