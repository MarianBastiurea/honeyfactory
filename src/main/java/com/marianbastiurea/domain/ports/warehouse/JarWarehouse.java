package com.marianbastiurea.domain.ports.warehouse;

import com.marianbastiurea.domain.model.ReservationAck;
import com.marianbastiurea.domain.enums.JarType;

import java.util.Map;

public interface JarWarehouse {
    ReservationAck reserve(Map<JarType, Integer> jars,
                           String orderId,
                           String idempotencyKey);

    void cancel(String reservationId, String reason);
}
