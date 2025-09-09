package com.marianbastiurea.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReserveResult(
        int orderNumber,
        List<ReserveLineResult> lines,
        BigDecimal totalRequestedKg,
        BigDecimal totalDeliveredKg,
        boolean fullyDelivered
) {}