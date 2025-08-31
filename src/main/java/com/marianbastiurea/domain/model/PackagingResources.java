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

    // moștenire / compatibilitate veche: întoarce capacitate în BORCANE pentru acel JarType
    int cratesFor(JarType type);

    // NOU: dacă nu vrei să implementezi încă, returnează map gol
    default Map<CrateType, Integer> cratesByType() {
        return Collections.emptyMap();
    }
}
