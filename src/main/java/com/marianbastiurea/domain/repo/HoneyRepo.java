package com.marianbastiurea.domain.repo;

import com.marianbastiurea.api.dto.DeliveryResult;
import com.marianbastiurea.domain.enums.HoneyType;

import java.math.BigDecimal;

public interface HoneyRepo {
    BigDecimal availableKg(HoneyType type);

    DeliveryResult processOrder(HoneyType type, int orderNumber, BigDecimal requestedKg);
}