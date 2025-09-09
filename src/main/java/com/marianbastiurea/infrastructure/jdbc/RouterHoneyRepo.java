package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.repo.HoneyRepo;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public class RouterHoneyRepo implements HoneyRepo {
    private final Map<HoneyType, NamedParameterJdbcTemplate> tplByType;

    public RouterHoneyRepo(Map<HoneyType, NamedParameterJdbcTemplate> tplByType) {
        this.tplByType = Map.copyOf(tplByType);
    }

    @Override
    public BigDecimal freeKg(HoneyType type) {
        var tpl = Objects.requireNonNull(tplByType.get(type), "No template for " + type);
        // Tabel simplu: honey_stock(available_kg NUMERIC). Ajustează după schema ta.
        String sql = "SELECT COALESCE(SUM(available_kg),0) FROM honey_stock";
        return tpl.queryForObject(sql, Map.of(), BigDecimal.class);
    }
}