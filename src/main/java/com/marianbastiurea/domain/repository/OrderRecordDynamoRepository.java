package com.marianbastiurea.domain.repository;

import com.marianbastiurea.domain.model.OrderRecord;
import com.marianbastiurea.persistence.nosql.OrderRecordEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class OrderRecordDynamoRepository implements OrderRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRecordDynamoRepository.class);

    private final DynamoDbTable<OrderRecordEntity> table;
    private final DynamoDbIndex<OrderRecordEntity> statusIndex;
    private final DynamoDbIndex<OrderRecordEntity> orderIndex;
    private final String tableName;
    private final String statusIndexName;
    private final String orderIndexName;

    public OrderRecordDynamoRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${dynamodb.tables.order-records:order_records}") String tableName,
            @Value("${dynamodb.indexes.order-records.status:status-index}") String statusIndexName,
            @Value("${dynamodb.indexes.order-records.order:order-index}") String orderIndexName
    ) {
        this.tableName = Objects.requireNonNull(tableName, "tableName");
        this.statusIndexName = Objects.requireNonNull(statusIndexName, "statusIndexName");
        this.orderIndexName = Objects.requireNonNull(orderIndexName, "orderIndexName");
        this.table = enhancedClient.table(this.tableName, TableSchema.fromBean(OrderRecordEntity.class));
        this.statusIndex = table.index(this.statusIndexName);
        this.orderIndex  = table.index(this.orderIndexName);

        log.info("OrderRecordDynamoRepository initialized (table='{}', statusIndex='{}', orderIndex='{}').",
                this.tableName, this.statusIndexName, this.orderIndexName);
    }

    @Override
    public OrderRecord save(OrderRecord order) {
        Objects.requireNonNull(order, "order");
        OrderRecordEntity entity = OrderRecordEntity.fromDomain(order);

        long t0 = System.nanoTime();
        try {
            table.putItem(entity);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("Saved OrderRecord in '{}' (id={}, orderNo={}, status={}) in {} ms.",
                    tableName, entity.getId(), entity.getOrderNumber(), entity.getStatus(), tookMs);
            return entity.toDomain();
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("Failed to save OrderRecord to '{}' in {} ms: {}", tableName, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    public Optional<OrderRecord> findById(String id) {
        Objects.requireNonNull(id, "id");
        long t0 = System.nanoTime();
        try {
            OrderRecordEntity e = table.getItem(Key.builder().partitionValue(id).build());
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.debug("findById('{}') -> {} ({} ms).", id, (e == null ? "NOT FOUND" : "FOUND"), tookMs);
            return Optional.ofNullable(e).map(OrderRecordEntity::toDomain);
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("findById('{}') FAILED in {} ms: {}", id, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    public List<OrderRecord> findByStatus(String status) {
        Objects.requireNonNull(status, "status");
        long t0 = System.nanoTime();
        try {
            var pages = statusIndex.query(r -> r.queryConditional(
                    QueryConditional.keyEqualTo(k -> k.partitionValue(status))
            ));

            List<OrderRecord> results = pages.stream()
                    .peek(p -> log.trace("findByStatus('{}') page size={}", status, p.items().size()))
                    .flatMap(page -> page.items().stream())
                    .map(OrderRecordEntity::toDomain)
                    .collect(Collectors.toList());

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("findByStatus('{}') -> {} item(s) via index '{}' in {} ms.",
                    status, results.size(), statusIndexName, tookMs);
            return results;
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("findByStatus('{}') FAILED via index '{}' in {} ms: {}",
                    status, statusIndexName, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    public List<OrderRecord> findByOrderNumber(Integer orderNumber) {
        Objects.requireNonNull(orderNumber, "orderNumber");
        long t0 = System.nanoTime();
        try {
            var pages = orderIndex.query(r -> r.queryConditional(
                    QueryConditional.keyEqualTo(k -> k.partitionValue(orderNumber))
            ));

            List<OrderRecord> results = pages.stream()
                    .peek(p -> log.trace("findByOrderNumber({}) page size={}", orderNumber, p.items().size()))
                    .flatMap(page -> page.items().stream())
                    .map(OrderRecordEntity::toDomain)
                    .collect(Collectors.toList());

            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("findByOrderNumber({}) -> {} item(s) via index '{}' in {} ms.",
                    orderNumber, results.size(), orderIndexName, tookMs);
            return results;
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("findByOrderNumber({}) FAILED via index '{}' in {} ms: {}",
                    orderNumber, orderIndexName, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    public void deleteById(String id) {
        Objects.requireNonNull(id, "id");
        long t0 = System.nanoTime();
        try {
            table.deleteItem(Key.builder().partitionValue(id).build());
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("deleteById('{}') -> OK ({} ms).", id, tookMs);
        } catch (Exception ex) {
            long tookMs = (System.nanoTime() - t0) / 1_000_000;
            log.error("deleteById('{}') FAILED in {} ms: {}", id, tookMs, ex.getMessage(), ex);
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }
}
