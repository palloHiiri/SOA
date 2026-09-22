package com.fuzis.tickets.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import javax.sql.DataSource;

@ApplicationScoped
public class DatabaseConfig {
    private HikariDataSource dataSource;

    @PostConstruct
    void init() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(value("TICKETS_DB_URL", "jdbc:postgresql://localhost:5433/tickets"));
        config.setUsername(value("TICKETS_DB_USERNAME", "postgres"));
        config.setPassword(value("TICKETS_DB_PASSWORD", "postgres"));
        config.setMaximumPoolSize(intValue("TICKETS_DB_POOL_SIZE", 10));
        config.setMinimumIdle(1);
        config.setPoolName("tickets-pool");
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.setInitializationFailTimeout(-1);
        dataSource = new HikariDataSource(config);
    }

    @Produces
    @ApplicationScoped
    public DataSource dataSource() {
        return dataSource;
    }

    @PreDestroy
    void destroy() {
        if (dataSource != null) dataSource.close();
    }

    private static String value(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int intValue(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
