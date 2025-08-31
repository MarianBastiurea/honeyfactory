package com.marianbastiurea.persistence.sql;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.Order;
import jakarta.persistence.*;

import java.util.HashMap;
import java.util.Map;

// ... restul importurilor și anotațiilor
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "honey_type", nullable = false, length = 32)
    private HoneyType honeyType;

    @Convert(converter = MapJarQuantitiesConverter.class)
    @Column(name = "jar_quantities", nullable = false, columnDefinition = "TEXT")
    private Map<JarType, Integer> jarQuantities = new HashMap<>();

    // trebuie să rămână fără-args pentru JPA; e ok să fie protected
    protected OrderEntity() {}

    // ▲ Factory public – îl vei apela din service
    public static OrderEntity of(Integer orderNumber,
                                 HoneyType honeyType,
                                 Map<JarType, Integer> jarQuantities) {
        OrderEntity e = new OrderEntity(); // accesibil în interiorul clasei
        e.orderNumber = orderNumber;
        e.honeyType = honeyType;
        e.jarQuantities = (jarQuantities == null) ? new HashMap<>() : new HashMap<>(jarQuantities);
        return e;
    }

    public Order toDomain() { return new Order(honeyType, jarQuantities, orderNumber); }

    public Integer getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(Integer orderNumber) {
        this.orderNumber = orderNumber;
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
}
