package com.marianbastiurea.domain.services;

import com.marianbastiurea.api.dto.PlaceOrderRequest;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;


public interface OrderService {


    List<Order> findByOrderNumber(Integer orderNumber);


    void logProcessing(int orderNumber,
                       Map<JarType, Integer> requestedByJar,
                       Map<JarType, Integer> deliveredByJar,
                       String reason);

    @Transactional
    Order create(PlaceOrderRequest req);
}
