package com.marianbastiurea.api.dto.response;

public record PlaceOrderResponse(
        boolean success,
        String message,
        Integer orderNumber
) {}