package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.HoneyType;

import java.math.BigDecimal;
import java.time.Instant;

public record AllocationLine(
        String warehouseKey,
        HoneyType type,
        BigDecimal quantityKg,
        Instant createdAt
) {}