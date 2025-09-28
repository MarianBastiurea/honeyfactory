package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.CrateType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.enums.LabelType;

import java.util.EnumMap;
import java.util.Map;

public record PackagingSnapshot(
        Map<JarType, StockRow> jars,
        Map<LabelType, StockRow> labels,
        Map<CrateType, StockRow> crates
) {
    public static PackagingSnapshot of(Map<JarType, StockRow> j,
                                       Map<LabelType, StockRow> l,
                                       Map<CrateType, StockRow> c) {
        return new PackagingSnapshot(new EnumMap<>(j), new EnumMap<>(l), new EnumMap<>(c));
    }

    public void bumpJar(JarType t, long newVer, int newFinal) {
        jars.put(t, new StockRow(newVer, newFinal));
    }

    public void bumpLabel(LabelType t, long newVer, int newFinal) {
        labels.put(t, new StockRow(newVer, newFinal));
    }

    public void bumpCrate(CrateType t, long newVer, int newFinal) {
        crates.put(t, new StockRow(newVer, newFinal));
    }
}