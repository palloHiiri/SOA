package com.fuzis.tickets.util;

import com.fuzis.tickets.exception.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SortParser {
    private SortParser() {
    }

    public record SortPart(String expression, String direction) {
    }

    public static List<SortPart> parse(List<String> raw, Map<String, String> allowed, String defaultField) {
        List<String> values = raw == null ? List.of() : raw;
        if (values.isEmpty()) {
            return List.of(new SortPart(allowed.get(defaultField), "ASC"));
        }

        List<SortPart> result = new ArrayList<>();
        for (String item : values) {
            if (item == null || item.isBlank()) {
                throw ApiException.badRequest("sort must not be blank");
            }
            String[] parts = item.split(",", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw ApiException.badRequest("Invalid sort expression: " + item);
            }
            String field = parts[0].trim();
            String expression = allowed.get(field);
            if (expression == null) {
                throw ApiException.badRequest("Unknown sort field: " + field);
            }
            String direction = parts.length == 1 ? "ASC" : parts[1].trim().toUpperCase();
            if (!direction.equals("ASC") && !direction.equals("DESC")) {
                throw ApiException.badRequest("Sort direction must be asc or desc: " + item);
            }
            result.add(new SortPart(expression, direction));
        }
        return result;
    }

    public static Map<String, String> map(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Pairs expected");
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i], pairs[i + 1]);
        }
        return result;
    }

    public static String toOrderBy(List<SortPart> parts) {
        return parts.stream()
                .map(part -> part.expression() + " " + part.direction())
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
    }
}
