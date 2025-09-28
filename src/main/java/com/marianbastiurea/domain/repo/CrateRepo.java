package com.marianbastiurea.domain.repo;

import com.marianbastiurea.domain.enums.JarType;

import java.util.Map;


public interface CrateRepo {
    void deliveredCrates(Map<JarType, Integer> plan);
}