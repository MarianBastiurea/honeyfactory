package com.marianbastiurea.domain.services;

import com.marianbastiurea.api.dto.PlaceOrderRequest;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderService {

    Order create(PlaceOrderRequest req);


    Optional<Order> findByOrderNumber(Integer orderNumber);


    Optional<Order> findByOrderNumberAndHoneyType(Integer orderNumber, HoneyType honeyType);

    List<Order> findByHoneyType(HoneyType honeyType);

    void deleteByOrderNumber(Integer orderNumber);
}

