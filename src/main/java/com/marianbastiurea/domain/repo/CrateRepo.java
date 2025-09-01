package com.marianbastiurea.domain.repo;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;

import java.math.BigDecimal;
import java.util.Map;

public interface CrateRepo {
    /** kg limitate de capacitatea de lăzi disponibilă. */
    BigDecimal freeAsKg(Map<JarType, Integer> requestedJars, HoneyType honeyType);
}