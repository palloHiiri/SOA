package com.fuzis.tickets.repository;

import jakarta.persistence.Tuple;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

final class JpaRows {
    private final List<Tuple> rows;
    private int index = -1;

    JpaRows(List<Tuple> rows) {
        this.rows = rows;
    }

    boolean next() {
        return ++index < rows.size();
    }

    Object getObject(String column) {
        return rows.get(index).get(column);
    }

    Object getObject(int column) {
        return rows.get(index).get(column - 1);
    }

    String getString(String column) {
        Object value = getObject(column);
        return value == null ? null : value.toString();
    }

    String getString(int column) {
        Object value = getObject(column);
        return value == null ? null : value.toString();
    }

    long getLong(String column) {
        Object value = getObject(column);
        return value == null ? 0 : ((Number) value).longValue();
    }

    long getLong(int column) {
        Object value = getObject(column);
        return value == null ? 0 : ((Number) value).longValue();
    }

    int getInt(String column) {
        return (int) getLong(column);
    }

    int getInt(int column) {
        return (int) getLong(column);
    }

    BigDecimal getBigDecimal(String column) {
        Object value = getObject(column);
        return value == null ? null : new BigDecimal(value.toString());
    }

    BigDecimal getBigDecimal(int column) {
        Object value = getObject(column);
        return value == null ? null : new BigDecimal(value.toString());
    }

    <T> T getObject(String column, Class<T> type) {
        Object value = getObject(column);
        if (value instanceof Date date && type == LocalDate.class) value = date.toLocalDate();
        if (value instanceof Timestamp date && type == OffsetDateTime.class)
            value = date.toInstant().atOffset(ZoneOffset.UTC);
        if (value instanceof Instant instant && type == OffsetDateTime.class)
            value = instant.atOffset(ZoneOffset.UTC);
        return type.cast(value);
    }
}
