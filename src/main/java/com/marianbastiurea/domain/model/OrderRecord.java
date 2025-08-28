package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.CrateType;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.enums.LabelType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class OrderRecord {
    private String orderId;
    private Instant createdAt;
    private String status;
    private String error;
    private String idempotencyKey;

    private HoneyType honeyType;
    private Map<JarType,Integer> requestedJarQuantities;
    private double plannedHoneyKg;
    private Map<JarType,Integer> plannedJars;
    private Map<LabelType,Integer> plannedLabels;
    private Map<CrateType,Integer> plannedCrates;

    private Map<String, ReservationAck> acks = new HashMap<>();

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public HoneyType getHoneyType() {
        return honeyType;
    }

    public void setHoneyType(HoneyType honeyType) {
        this.honeyType = honeyType;
    }

    public Map<JarType, Integer> getRequestedJarQuantities() {
        return requestedJarQuantities;
    }

    public void setRequestedJarQuantities(Map<JarType, Integer> requestedJarQuantities) {
        this.requestedJarQuantities = requestedJarQuantities;
    }

    public double getPlannedHoneyKg() {
        return plannedHoneyKg;
    }

    public void setPlannedHoneyKg(double plannedHoneyKg) {
        this.plannedHoneyKg = plannedHoneyKg;
    }

    public Map<JarType, Integer> getPlannedJars() {
        return plannedJars;
    }

    public void setPlannedJars(Map<JarType, Integer> plannedJars) {
        this.plannedJars = plannedJars;
    }

    public Map<LabelType, Integer> getPlannedLabels() {
        return plannedLabels;
    }

    public void setPlannedLabels(Map<LabelType, Integer> plannedLabels) {
        this.plannedLabels = plannedLabels;
    }

    public Map<CrateType, Integer> getPlannedCrates() {
        return plannedCrates;
    }

    public void setPlannedCrates(Map<CrateType, Integer> plannedCrates) {
        this.plannedCrates = plannedCrates;
    }

    public Map<String, ReservationAck> getAcks() {
        return acks;
    }

    public void setAcks(Map<String, ReservationAck> acks) {
        this.acks = acks;
    }

    public void putAck(String key, ReservationAck ack) { this.acks.put(key, ack); }
}
