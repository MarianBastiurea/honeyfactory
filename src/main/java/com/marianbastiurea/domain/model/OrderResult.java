package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.CrateType;
import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.enums.LabelType;

import java.util.Map;

public record OrderResult(
        HoneyType honeyType,
        double honeyKg,
        Map<JarType, Integer> jars,
        Map<LabelType, Integer> labels,
        Map<CrateType, Integer> crates
) {}
