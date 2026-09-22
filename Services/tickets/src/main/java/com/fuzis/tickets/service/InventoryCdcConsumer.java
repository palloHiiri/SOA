package com.fuzis.tickets.service;

import com.fuzis.tickets.config.KafkaConfig;
import com.fuzis.tickets.repository.CdcRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@Startup
public class InventoryCdcConsumer {

    private static final Logger LOG = Logger.getLogger(InventoryCdcConsumer.class.getName());

    @Inject
    private KafkaConfig kafkaConfig;

    @Inject
    private CdcRepository cdcRepository;

    @Resource(lookup = "java:comp/DefaultManagedExecutorService")
    private ManagedExecutorService executor;

    private volatile boolean running;
    private volatile Future<?> task;
    private volatile KafkaConsumer<String, String> consumer;

    @PostConstruct
    void start() {
        running = true;
        task = executor.submit(this::consumeLoop);
    }

    @PreDestroy
    void stop() {
        running = false;

        KafkaConsumer<String, String> currentConsumer = consumer;
        if (currentConsumer != null) {
            currentConsumer.wakeup();
        }

        Future<?> currentTask = task;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }

    private void consumeLoop() {
        try (KafkaConsumer<String, String> kafka =
                     new KafkaConsumer<>(kafkaConfig.consumerProperties())) {
            consumer = kafka;
            kafka.subscribe(List.of(kafkaConfig.topic()));

            LOG.info(() -> "Subscribed to Inventory CDC topic: " + kafkaConfig.topic());

            while (running) {
                try {
                    for (ConsumerRecord<String, String> record :
                            kafka.poll(Duration.ofMillis(kafkaConfig.pollTimeoutMs()))) {

                        if (record.value() == null) {
                            // Tombstones do not carry an Inventory snapshot, but their offset
                            // still has to be advanced or the same tombstone would be retried forever.
                            kafka.commitSync();
                            continue;
                        }

                        process(record.value());

                        // Commit only after the corresponding DB write succeeded.
                        kafka.commitSync();
                    }
                } catch (org.apache.kafka.common.errors.WakeupException e) {
                    if (running) {
                        throw e;
                    }
                } catch (Exception e) {
                    LOG.log(
                            Level.SEVERE,
                            "Failed to process Inventory CDC record; offset will be retried",
                            e);

                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                LOG.log(Level.SEVERE, "Inventory CDC consumer stopped unexpectedly", e);
            }
        } finally {
            consumer = null;
        }
    }

    private void process(String rawValue) throws Exception {
        JsonObject value = object(rawValue);

        String op = value.getString("__op", "");
        if ("d".equals(op)) {
            return;
        }

        int trainSetId = numberOrString(value, "train_set_id").intValueExact();
        long version = numberOrString(value, "version").longValueExact();

        JsonValue dataValue = value.get("data");
        if (dataValue == null || dataValue.getValueType() == JsonValue.ValueType.NULL) {
            throw new IllegalArgumentException("Inventory CDC record has no data field");
        }

        String data = dataValue.getValueType() == JsonValue.ValueType.STRING
                ? ((JsonString) dataValue).getString()
                : dataValue.toString();

        JsonObject dataJson = object(data);
        JsonValue dataObject = dataJson.get("id");
        if (dataObject == null) {
            throw new IllegalArgumentException("Inventory CDC data has no train-set id");
        }

        int dataTrainSetId = numberOrString(dataJson, "id").intValueExact();
        if (dataTrainSetId != trainSetId) {
            throw new IllegalArgumentException("CDC train_set_id does not match data.id");
        }

        cdcRepository.insertSnapshot(trainSetId, data, version);
    }

    private static JsonObject object(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    private static java.math.BigDecimal numberOrString(JsonObject object, String field) {
        JsonValue value = object.get(field);

        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            throw new IllegalArgumentException("Missing field: " + field);
        }

        if (value.getValueType() == JsonValue.ValueType.NUMBER) {
            return object.getJsonNumber(field).bigDecimalValue();
        }

        if (value.getValueType() == JsonValue.ValueType.STRING) {
            return new java.math.BigDecimal(object.getString(field));
        }

        throw new IllegalArgumentException("Invalid field: " + field);
    }
}
