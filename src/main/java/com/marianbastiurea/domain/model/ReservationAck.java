package com.marianbastiurea.domain.model;

import java.time.Instant;
import java.util.Map;

public record ReservationAck(
        String reservationId,
        String resource,
        int requestedUnits,
        double requestedKg,
        Instant reservedAt,
        Map<String, String> meta
) {}