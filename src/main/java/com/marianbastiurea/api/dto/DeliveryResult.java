package com.marianbastiurea.api.dto;


import java.math.BigDecimal;

public record DeliveryResult(
        BigDecimal deliveredKg,
        BigDecimal newStock,
        long newVersion) {
}
