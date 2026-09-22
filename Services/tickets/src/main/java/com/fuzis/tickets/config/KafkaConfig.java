package com.fuzis.tickets.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Properties;

@ApplicationScoped
public class KafkaConfig {
    public Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, value("TICKETS_KAFKA_BOOTSTRAP_SERVERS", "localhost:19092"));
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, value("TICKETS_KAFKA_GROUP_ID", "tickets"));
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, value("TICKETS_KAFKA_AUTO_OFFSET_RESET", "earliest"));
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");
        return properties;
    }

    public String topic() {
        return value("TICKETS_KAFKA_TOPIC", "inventory.train_set_snapshots");
    }

    public long pollTimeoutMs() {
        String value = System.getenv("TICKETS_KAFKA_POLL_TIMEOUT_MS");
        if (value == null || value.isBlank()) return 1000L;
        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException ex) {
            return 1000L;
        }
    }

    private static String value(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
