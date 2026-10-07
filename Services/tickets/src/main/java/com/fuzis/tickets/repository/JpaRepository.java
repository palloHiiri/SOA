package com.fuzis.tickets.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class JpaRepository {
    @PersistenceContext(unitName = "tickets")
    protected EntityManager entityManager;

    protected JpaRows rows(String sql, List<Object> parameters) {
        Map<Integer, Object> bindings = new HashMap<>();
        for (int index = 0; index < parameters.size(); index++)
            bindings.put(index + 1, parameters.get(index));
        return rows(sql, bindings);
    }

    protected JpaRows rows(String sql, Map<Integer, Object> parameters) {
        Query query = entityManager.createNativeQuery(numberParameters(sql), Tuple.class);
        parameters.forEach(query::setParameter);
        return new JpaRows(query.getResultList());
    }

    protected int execute(String sql, Map<Integer, Object> parameters) {
        Query query = entityManager.createNativeQuery(numberParameters(sql));
        parameters.forEach(query::setParameter);
        return query.executeUpdate();
    }

    private static String numberParameters(String sql) {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        int position = 0;
        for (int index = 0; index < sql.length(); index++) {
            char value = sql.charAt(index);
            if (value == '\'') quoted = !quoted;
            if (value == '?' && !quoted) result.append('?').append(++position);
            else result.append(value);
        }
        return result.toString();
    }
}
