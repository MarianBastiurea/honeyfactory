package com.marianbastiurea.domain.repository;

import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.domain.model.OrderRecord;

public interface OrderRecordRepository {

    String save(Order order);


    default String save(OrderRecord r) {
        return save(new Order(r.honeyType(), r.jarQuantities(), r.orderNumber()));
    }
}
