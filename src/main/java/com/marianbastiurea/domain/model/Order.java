package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.HoneyType;
import com.marianbastiurea.domain.enums.JarType;

import java.util.Map;

public record Order(HoneyType honeyType,
                    Map<JarType, Integer> jarQuantities, Integer orderNumber) {
}
