package com.marianbastiurea.domain.repository;


import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.persistence.sql.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Integer> {
    List<OrderEntity> findByHoneyType(HoneyType honeyType);
}
