package com.marianbastiurea.domain.repo;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;

import java.math.BigDecimal;
import java.util.Map;

public interface LabelRepo {
    BigDecimal freeAsKg(Map<JarType, Integer> requestedJars, HoneyType honeyType);
}
