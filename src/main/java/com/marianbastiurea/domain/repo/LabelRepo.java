package com.marianbastiurea.domain.repo;

import com.marianbastiurea.domain.enums.JarType;

import java.util.Map;

public interface LabelRepo {
    void deliveredLabels(Map<JarType, Integer> plan);
}
