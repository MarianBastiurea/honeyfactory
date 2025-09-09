package com.marianbastiurea.persistence.nosql;
import com.marianbastiurea.domain.model.OrderRecord;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class OrderRecordEntity {
    private String id;
    private String status;
    private Long createdAtEpoch;

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getCreatedAtEpoch() { return createdAtEpoch; }
    public void setCreatedAtEpoch(Long createdAtEpoch) { this.createdAtEpoch = createdAtEpoch; }

    // --- mapping methods ---
    public static OrderRecordEntity fromDomain(OrderRecord d) {
        OrderRecordEntity e = new OrderRecordEntity();
        e.setId(d.orderId());
        e.setStatus(d.status().name());
        e.setCreatedAtEpoch(d.createdAt().toEpochMilli());
        return e;
    }

    public OrderRecord toDomain() {
        return new OrderRecord(
                id,
                OrderRecord.Status.valueOf(status),
                java.time.Instant.ofEpochMilli(createdAtEpoch)
        );
    }

}
