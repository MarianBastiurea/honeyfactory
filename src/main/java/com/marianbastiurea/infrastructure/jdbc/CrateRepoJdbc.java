package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.repo.CrateRepo;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Simplu: crate_stock(jar_type TEXT, slot_capacity INT, available_slots INT).
 * kg = sum(min(requestedJars, slot_capacity*available_slots) * jarKg)
 */


public class CrateRepoJdbc implements CrateRepo {
    private final NamedParameterJdbcTemplate tpl;

    public CrateRepoJdbc(NamedParameterJdbcTemplate tpl) { this.tpl = tpl; }

    @Override
    public BigDecimal freeAsKg(Map<JarType, Integer> requestedJars, HoneyType honeyType) {
        String sql = "SELECT jar_type, slot_capacity, available_slots FROM crate_stock";
        Map<JarType, int[]> data = tpl.query(sql, rs -> {
            Map<JarType, int[]> m = new EnumMap<>(JarType.class);
            while (rs.next()) {
                JarType type = JarType.valueOf(rs.getString("jar_type"));
                int cap = rs.getInt("slot_capacity");
                int slots = rs.getInt("available_slots");
                m.put(type, new int[]{cap, slots});
            }
            return m;
        });

        BigDecimal kg = BigDecimal.ZERO;
        for (var e : requestedJars.entrySet()) {
            JarType type = e.getKey();
            int req = e.getValue();
            int[] capSlots = data.getOrDefault(type, new int[]{0, 0});
            int jarCapacity = capSlots[0] * capSlots[1];
            int canCrate = Math.min(req, jarCapacity);
            kg = kg.add(type.kgPerJar().multiply(BigDecimal.valueOf(canCrate)));
        }
        return kg;
    }
}