package com.marianbastiurea.domain.enums;

public enum CrateType {
    CRATE200(JarType.JAR200, 48),
    CRATE400(JarType.JAR400, 24),
    CRATE800(JarType.JAR800, 12);

    private final JarType jarType;
    private final int jarsPerCrate;

    CrateType(JarType jarType, int jarsPerCrate) {
        this.jarType = jarType;
        this.jarsPerCrate = jarsPerCrate;
    }

    public int cratesNeededForJars(int jarsCount) {
        return (int) Math.ceil(jarsCount / (double) jarsPerCrate);
    }


    public int jarsCapacityForCrates(int cratesCount) {
        return cratesCount * jarsPerCrate;
    }

    public static CrateType forJarType(JarType jarType) {
        for (CrateType ct : values()) {
            if (ct.jarType == jarType) return ct;
        }
        throw new IllegalArgumentException("No crate type for jar type: " + jarType);
    }
}
