package com.marianbastiurea.domain.repo;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;

import java.math.BigDecimal;
import java.util.Map;

public interface JarRepo {
    /** kg limitate de numărul de borcane disponibile pentru distribuția cerută. */
    BigDecimal freeAsKg(Map<JarType, Integer> requestedJars, HoneyType honeyType);
}