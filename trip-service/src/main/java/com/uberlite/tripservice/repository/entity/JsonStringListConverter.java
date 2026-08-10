package com.uberlite.tripservice.repository.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a small ordered list of ids as a JSON array in a {@code text} column.
 *
 * <p>Used for the declined-driver list. A child table would be the textbook choice, but the list is
 * bounded by the k=3 retry budget, is only ever read and written as a whole, and is never queried
 * by element — so a join table would be cost without benefit.
 */
@Converter
public class JsonStringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize string list", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(OBJECT_MAPPER.readValue(dbData, TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize string list", e);
        }
    }
}

