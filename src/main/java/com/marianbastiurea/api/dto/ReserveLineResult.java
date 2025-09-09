package com.marianbastiurea.api.dto;

import com.marianbastiurea.domain.model.AllocationLine;

import java.math.BigDecimal;

public record ReserveLineResult(
        AllocationLine line,
        BigDecimal requestedKg,
        BigDecimal deliveredKg,
        BigDecimal newStock,
        long newVersion
) {}