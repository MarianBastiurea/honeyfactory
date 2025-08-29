package com.marianbastiurea.domain.model;

public record OrderRecord(String orderId, Status status, java.time.Instant createdAt) {
    public enum Status { NEW, PROCESSING, DONE, CANCELED }
}
