package com.marianbastiurea.domain.ports.warehouse;

import com.marianbastiurea.domain.model.ReservationAck;
import com.marianbastiurea.domain.enums.LabelType;

import java.util.Map;

public interface LabelWarehouse {
    ReservationAck reserve(Map<LabelType, Integer> labels,
                           String orderId,
                           String idempotencyKey);

    void cancel(String reservationId, String reason);
}
