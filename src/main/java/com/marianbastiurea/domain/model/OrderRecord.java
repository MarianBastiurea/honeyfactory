package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;

import java.time.Instant;
import java.util.Map;

public record OrderRecord(
        String id,
        Integer orderNumber,
        HoneyType honeyType,
        Map<JarType, Integer> jarQuantities,
        Instant executedAt,
        Status status,
        String note
) {
    public enum Status { NEW, PROCESSING, DONE, CANCELED }
}