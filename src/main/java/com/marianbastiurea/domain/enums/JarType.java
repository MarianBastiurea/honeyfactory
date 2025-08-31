package com.marianbastiurea.domain.enums;

import java.math.BigDecimal;

public enum JarType {
    JAR200(new BigDecimal("0.28")),
    JAR400(new BigDecimal("0.56")),
    JAR800(new BigDecimal("1"));

    private final BigDecimal kgPerJar;
    JarType(BigDecimal kgPerJar) { this.kgPerJar = kgPerJar; }
    public BigDecimal kgPerJar() { return kgPerJar; }
}