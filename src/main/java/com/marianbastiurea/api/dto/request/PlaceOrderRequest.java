package com.marianbastiurea.api.dto.request;



import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;

import java.util.Map;

/**
 * Simple request payload for placing an order.
 * Client provides the honey type and the desired jar quantities.
 * The server assigns/generates the orderNumber.
 */
public class PlaceOrderRequest {

    private HoneyType honeyType;
    private Map<JarType, Integer> jarQuantities;

    public PlaceOrderRequest() {
        // default constructor for JSON deserialization
    }

    public PlaceOrderRequest(HoneyType honeyType, Map<JarType, Integer> jarQuantities) {
        this.honeyType = honeyType;
        this.jarQuantities = jarQuantities;
    }

    public HoneyType getHoneyType() {
        return honeyType;
    }

    public void setHoneyType(HoneyType honeyType) {
        this.honeyType = honeyType;
    }

    public Map<JarType, Integer> getJarQuantities() {
        return jarQuantities;
    }

    public void setJarQuantities(Map<JarType, Integer> jarQuantities) {
        this.jarQuantities = jarQuantities;
    }

    /**
     * Helper to convert this request into the domain Order once an order number is available.
     */
    public Order toDomain(int orderNumber) {
        return new Order(honeyType, jarQuantities, orderNumber);
    }

    @Override
    public String toString() {
        return "PlaceOrderRequest{" +
                "honeyType=" + honeyType +
                ", jarQuantities=" + jarQuantities +
                '}';
    }
}
