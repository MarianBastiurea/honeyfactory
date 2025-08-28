package com.marianbastiurea.domain.ports.warehouse;

import com.marianbastiurea.domain.model.ReservationAck;
import com.marianbastiurea.domain.enums.CrateType;

import java.util.Map;

public interface CrateWarehouse {
    ReservationAck reserve(Map<CrateType, Integer> crates,
                           String orderId,
                           String idempotencyKey);

    void cancel(String reservationId, String reason);
}