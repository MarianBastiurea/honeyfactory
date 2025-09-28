package com.marianbastiurea.domain.repo;

import com.marianbastiurea.domain.enums.JarType;

import java.util.Map;


public interface JarRepo {
    void deliveredJars(Map<JarType, Integer> plan);
}