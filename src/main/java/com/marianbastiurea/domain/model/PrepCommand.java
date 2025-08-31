package com.marianbastiurea.domain.model;

import com.marianbastiurea.domain.enums.JarType;

public record PrepCommand(String warehouseKey, PrepResource resource, JarType jarType, int quantity) {
    public enum PrepResource { JARS, LABELS, CRATES }
}
