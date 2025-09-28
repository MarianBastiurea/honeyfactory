package com.marianbastiurea.persistence.nosql;

import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.Map;

@DynamoDbBean
public class OrderRecordEntity {

    private String pk;
    private String sk;
    private Integer orderNumber;
    private String honeyType;
    private Map<String, Integer> jars;
    private String status;
    private Instant ts;
    private String reason;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("pk")
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    @DynamoDbAttribute("sk")
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }

    @DynamoDbAttribute("orderNumber")
    public Integer getOrderNumber() { return orderNumber; }
    public void setOrderNumber(Integer orderNumber) { this.orderNumber = orderNumber; }

    @DynamoDbAttribute("honeyType")
    public String getHoneyType() { return honeyType; }
    public void setHoneyType(String honeyType) { this.honeyType = honeyType; }

    @DynamoDbAttribute("jars")
    public Map<String, Integer> getJars() { return jars; }
    public void setJars(Map<String, Integer> jars) { this.jars = jars; }

    @DynamoDbAttribute("status")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbAttribute("ts")
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }

    @DynamoDbAttribute("reason")
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }


    public static OrderRecordEntity received(Integer orderNumber,
                                             String honeyType,
                                             Map<String, Integer> jars,
                                             Instant ts) {
        Assert.notNull(orderNumber, "orderNumber");
        OrderRecordEntity e = new OrderRecordEntity();
        e.setPk("ORDER#" + orderNumber);
        e.setSk("EVENT#" + ts);
        e.setOrderNumber(orderNumber);
        e.setHoneyType(honeyType);
        e.setJars(jars);
        e.setStatus("RECEIVED");
        e.setTs(ts);
        return e;
    }
}
