package com.marianbastiurea.api.dto.request;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;

import java.util.Map;

public record PlaceOrderRequest(
        HoneyType honeyType,
        Map<JarType, Integer> jarQuantities,
        Integer orderNumber
) {}
