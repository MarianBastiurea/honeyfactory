package com.marianbastiurea.api.dto;

public record PlaceOrderResponse(
        boolean success,
        String message,
        Integer orderNumber
) {}