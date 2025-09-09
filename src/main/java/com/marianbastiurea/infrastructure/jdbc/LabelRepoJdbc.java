package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.repo.LabelRepo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@Repository
public class LabelRepoJdbc implements LabelRepo {
    private final NamedParameterJdbcTemplate tpl;

    public LabelRepoJdbc(@Qualifier("labelsTpl") NamedParameterJdbcTemplate tpl) { this.tpl = tpl; }


    @Override
    public BigDecimal freeAsKg(Map<JarType, Integer> requestedJars, HoneyType honeyType) {
        // Tabel: label_stock(jar_type TEXT, available_labels INT)
        String sql = "SELECT jar_type, available_labels FROM label_stock";
        Map<JarType, Integer> available = tpl.query(sql, rs -> {
            Map<JarType, Integer> m = new EnumMap<>(JarType.class);
            while (rs.next()) {
                JarType type = JarType.valueOf(rs.getString("jar_type"));
                m.put(type, rs.getInt("available_labels"));
            }
            return m;
        });

        BigDecimal kg = BigDecimal.ZERO;
        for (var e : requestedJars.entrySet()) {
            JarType type = e.getKey();
            int req = e.getValue();
            int avail = available.getOrDefault(type, 0);
            int canLabel = Math.min(req, avail); // 1 etichetă per borcan
            kg = kg.add(type.kgPerJar().multiply(BigDecimal.valueOf(canLabel)));
        }
        return kg;
    }
}