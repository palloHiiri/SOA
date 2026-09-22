package com.fuzis.ssoident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Locale;
import java.util.Map;

@Service
public class KafkaEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    private final String registrationTopic;
    private final String verificationSmsTopic;
    private final String verificationEmailTopic;

    public KafkaEventPublisher(
    KafkaTemplate<String, String> kafkaTemplate,
    ObjectMapper mapper,
    @Value("${sso.kafka.user-registration-topic}") String registrationTopic,
    @Value("${sso.kafka.verification-sms-topic}") String verificationSmsTopic,
    @Value("${sso.kafka.verification-email-topic}") String verificationEmailTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
        this.registrationTopic = registrationTopic;
        this.verificationSmsTopic = verificationSmsTopic;
        this.verificationEmailTopic = verificationEmailTopic;
    }

    public Mono<Void> publishUserRegistration(Map<String, Object> payload) {
        return publish(registrationTopic, payload);
    }

    public Mono<Void> publishVerificationCode(Map<String, Object> payload) {
        Object channel = payload.get("channel");
        if (channel == null || channel.toString().isBlank()) {
            return Mono.error(new IllegalArgumentException("Verification code event must contain channel"));
        }

        String normalized = channel.toString().trim().toLowerCase(Locale.ROOT);
        String topic = switch (normalized) {
            case "sms" -> verificationSmsTopic;
            case "email" -> verificationEmailTopic;
            default -> throw new IllegalArgumentException("Unsupported verification channel: " + channel);
        }
        ;
        return publish(topic, payload);
    }

    private Mono<Void> publish(String topic, Object payload) {
        return Mono.fromCallable(() -> mapper.writeValueAsString(payload))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(json -> Mono.fromFuture(kafkaTemplate.send(topic, json)))
        .then();
    }
}
