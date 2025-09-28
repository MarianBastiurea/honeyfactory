package com.marianbastiurea.domain.repository;

import com.marianbastiurea.domain.model.Order;
import com.marianbastiurea.persistence.nosql.OrderRecordEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class OrderRecordDynamoRepository implements OrderRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRecordDynamoRepository.class);
    private final DynamoDbTable<OrderRecordEntity> table;

    public OrderRecordDynamoRepository(
            DynamoDbEnhancedClient enhanced,

            @Value("${dynamodb.tables.order-records:${app.dynamo.orderRecordsTable:order_records}}") String tableName
    ) {
        this.table = enhanced.table(tableName, TableSchema.fromBean(OrderRecordEntity.class));
        log.info("DynamoDB table bound: {}", tableName);

        try {
            table.describeTable();
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            log.error("DynamoDB table '{}' not found in the configured region. Create it or fix the name.", tableName);

        }
    }

    @Override
    public String save(Order order) {
        Instant now = Instant.now();

        Map<String, Integer> jarsAsString = order.jarQuantities().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));

        OrderRecordEntity entity = OrderRecordEntity.received(
                order.orderNumber(),
                order.honeyType().name(),
                jarsAsString,
                now
        );

        table.putItem(entity);
        log.info("dynamo.putItem ok | pk={} sk={} order={}", entity.getPk(), entity.getSk(), order.orderNumber());
        return entity.getSk();
    }
}
