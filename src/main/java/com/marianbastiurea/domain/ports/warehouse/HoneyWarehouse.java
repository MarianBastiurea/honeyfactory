package com.marianbastiurea.domain.ports.warehouse;

import com.marianbastiurea.domain.model.ReservationAck;
import com.marianbastiurea.domain.enums.HoneyType;

public interface HoneyWarehouse {
    ReservationAck reserve(HoneyType type, double kg, String orderId, String idempotencyKey);
    void cancel(String reservationId, String reason);
    double availability(); // opțional
}