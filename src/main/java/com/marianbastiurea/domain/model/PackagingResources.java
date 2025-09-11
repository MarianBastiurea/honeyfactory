package com.marianbastiurea.domain.model;


import com.marianbastiurea.domain.enums.CrateType;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

public interface PackagingResources {
    BigDecimal honeyFor(HoneyType type);
    int jarsFor(JarType type);
    int labelsFor(JarType type);
    int cratesFor(JarType type);
    default Map<CrateType, Integer> cratesByType() {
        return Collections.emptyMap();
    }
}
