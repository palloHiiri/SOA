package com.fuzis.ssoident.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class RoleRepository {
    private final DatabaseClient db;

    public RoleRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<RoleRecord> findByName(String name) {
        return db.sql("""
            SELECT id, name, description, is_default
            FROM roles
            WHERE name = :name
            """)
        .bind("name", name)
        .map((row, meta) -> new RoleRecord(
        row.get("id", Integer.class),
        row.get("name", String.class),
        row.get("description", String.class),
        row.get("is_default", Boolean.class)
        ))
        .one();
    }

    public Mono<RoleRecord> findDefault() {
        return db.sql("""
            SELECT id, name, description, is_default
            FROM roles
            WHERE is_default = TRUE
            """)
        .map((row, meta) -> new RoleRecord(
        row.get("id", Integer.class),
        row.get("name", String.class),
        row.get("description", String.class),
        row.get("is_default", Boolean.class)
        ))
        .one();
    }

    public Flux<RoleRecord> findAll() {
        return db.sql("""
            SELECT id, name, description, is_default
            FROM roles
            ORDER BY id
            """)
        .map((row, meta) -> new RoleRecord(
        row.get("id", Integer.class),
        row.get("name", String.class),
        row.get("description", String.class),
        row.get("is_default", Boolean.class)
        ))
        .all();
    }

    public record RoleRecord(Integer id, String name, String description, Boolean isDefault) {
    }
}
