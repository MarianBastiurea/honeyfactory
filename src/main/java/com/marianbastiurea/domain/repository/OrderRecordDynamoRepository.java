package com.marianbastiurea.domain.repository;

import com.marianbastiurea.domain.model.OrderRecord;
import com.marianbastiurea.persistence.nosql.OrderRecordEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class OrderRecordDynamoRepository implements OrderRecordRepository {

    private final DynamoDbTable<OrderRecordEntity> table;
    private final DynamoDbIndex<OrderRecordEntity> statusIndex;

    public OrderRecordDynamoRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${dynamodb.tables.order-records:order_records}") String tableName,
            @Value("${dynamodb.indexes.order-records.status:status-index}") String statusIndexName
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(OrderRecordEntity.class));
        this.statusIndex = table.index(statusIndexName);
    }

    @Override
    public OrderRecord save(OrderRecord order) {
        OrderRecordEntity entity = OrderRecordEntity.fromDomain(order);
        table.putItem(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<OrderRecord> findById(String id) {
        OrderRecordEntity e = table.getItem(Key.builder().partitionValue(id).build());
        return Optional.ofNullable(e).map(OrderRecordEntity::toDomain);
    }

    @Override
    public List<OrderRecord> findByStatus(String status) {
        return statusIndex.query(q -> q.queryConditional(
                        QueryConditional.keyEqualTo(Key.builder().partitionValue(status).build())
                ))
                .stream()
                .flatMap(page -> page.items().stream())
                .map(OrderRecordEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        table.deleteItem(Key.builder().partitionValue(id).build());
    }
}
