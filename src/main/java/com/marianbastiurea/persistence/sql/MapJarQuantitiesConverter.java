package com.marianbastiurea.persistence.sql;


import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianbastiurea.domain.enums.JarType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;

@Converter(autoApply = false)
public class MapJarQuantitiesConverter implements AttributeConverter<Map<JarType, Integer>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JavaType MAP_TYPE =
            MAPPER.getTypeFactory().constructMapType(Map.class, JarType.class, Integer.class);

    @Override
    public String convertToDatabaseColumn(Map<JarType, Integer> attribute) {
        try {
            return attribute == null ? "{}" : MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize jarQuantities to JSON", e);
        }
    }

    @Override
    public Map<JarType, Integer> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isBlank()) return new HashMap<>();
            return MAPPER.readValue(dbData, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize jarQuantities from JSON", e);
        }
    }
}

