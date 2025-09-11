package com.marianbastiurea.persistence.nosql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.model.OrderRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@DynamoDbBean
public class OrderRecordEntity {

    private static final Logger log = LoggerFactory.getLogger(OrderRecordEntity.class);


    private String pk;
    private Instant executedAt;
    private Integer orderNumber;
    private HoneyType honeyType;
    private Map<JarType,Integer> jarQuantities;
    private String status; // String ca să fie GSI-friendly
    private String note;


    @DynamoDbPartitionKey
    @DynamoDbAttribute("pk")
    public String getId() { return pk; }
    public void setId(String id) { this.pk = id; }


    @DynamoDbIgnore
    public String getPk() { return pk; }
    @DynamoDbIgnore
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbAttribute("executedAt")
    public Instant getExecutedAt(){ return executedAt; }
    public void setExecutedAt(Instant executedAt){ this.executedAt = executedAt; }

    public HoneyType getHoneyType(){ return honeyType; }
    public void setHoneyType(HoneyType honeyType){ this.honeyType = honeyType; }

    @DynamoDbConvertedBy(JarQuantitiesConverter.class)
    public Map<JarType,Integer> getJarQuantities(){ return jarQuantities; }
    public void setJarQuantities(Map<JarType,Integer> jarQuantities){ this.jarQuantities = jarQuantities; }


    @DynamoDbSecondaryPartitionKey(indexNames = "order-index")
    public Integer getOrderNumber(){ return orderNumber; }
    public void setOrderNumber(Integer orderNumber){ this.orderNumber = orderNumber; }


    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    public String getStatus(){ return status; }
    public void setStatus(String status){ this.status = status; }

    public String getNote(){ return note; }
    public void setNote(String note){ this.note = note; }


    public static OrderRecordEntity fromDomain(OrderRecord d) {
        Objects.requireNonNull(d, "order record domain must not be null");
        OrderRecordEntity e = new OrderRecordEntity();
        String id = (d.id() != null && !d.id().isBlank())
                ? d.id()
                : buildPk(d.orderNumber(), d.honeyType());
        e.setId(id);
        e.setExecutedAt(d.executedAt());
        e.setOrderNumber(d.orderNumber());
        e.setHoneyType(d.honeyType());
        e.setJarQuantities(d.jarQuantities());
        e.setStatus(d.status() != null ? d.status().name() : null);
        e.setNote(d.note());

        if (log.isDebugEnabled()) {
            log.debug("fromDomain -> id={}, orderNo={}, honey={}, status={}",
                    e.getId(), e.getOrderNumber(), e.getHoneyType(), e.getStatus());
        }
        return e;
    }

    public OrderRecord toDomain() {
        OrderRecord.Status st = (status != null) ? OrderRecord.Status.valueOf(status) : null;
        if (log.isDebugEnabled()) {
            log.debug("toDomain <- id={}, orderNo={}, honey={}, status={}",
                    getId(), getOrderNumber(), getHoneyType(), getStatus());
        }
        return new OrderRecord(
                getId(),
                getOrderNumber(),
                getHoneyType(),
                getJarQuantities(),
                getExecutedAt(),
                st,
                getNote()
        );
    }

    public static String buildPk(Integer orderNumber, HoneyType honeyType) {
        Objects.requireNonNull(orderNumber, "orderNumber must not be null");
        Objects.requireNonNull(honeyType, "honeyType must not be null");
        return "ORD#" + orderNumber + "#" + honeyType.name();
    }


    public static class JarQuantitiesConverter implements AttributeConverter<Map<JarType,Integer>> {
        private static final ObjectMapper M = new ObjectMapper();

        @Override
        public AttributeValue transformFrom(Map<JarType, Integer> input) {
            try {
                if (input == null || input.isEmpty()) {
                    return AttributeValue.builder().s("").build();
                }
                Map<String,Integer> asStringKeys = input.entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey().name(),
                                Map.Entry::getValue,
                                (a,b) -> b,
                                LinkedHashMap::new));
                String json = M.writeValueAsString(asStringKeys);
                return AttributeValue.builder().s(json).build();
            } catch (Exception ex) {
                throw new RuntimeException("Failed to convert jarQuantities to JSON", ex);
            }
        }

        @Override
        public Map<JarType, Integer> transformTo(AttributeValue av) {
            try {
                String s = av.s();
                if (s == null || s.isBlank()) return null;
                Map<String,Integer> raw = M.readValue(s, new TypeReference<Map<String,Integer>>(){});
                return raw.entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> JarType.valueOf(e.getKey()),
                                Map.Entry::getValue,
                                (a,b) -> b,
                                LinkedHashMap::new));
            } catch (Exception ex) {
                throw new RuntimeException("Failed to parse jarQuantities JSON", ex);
            }
        }

        @Override public EnhancedType<Map<JarType, Integer>> type() {
            return (EnhancedType) EnhancedType.of(Map.class);
        }

        @Override public AttributeValueType attributeValueType() { return AttributeValueType.S; }
    }
}
