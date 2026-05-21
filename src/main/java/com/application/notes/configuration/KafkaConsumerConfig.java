package com.application.notes.configuration;

import com.application.notes.event.NoteEvent;
import com.application.notes.event.SummaryReadyEvent;
import com.application.notes.event.SummaryRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:notes-service}")
    private String groupId;

    private Map<String, Object> baseConfig(String groupSuffix) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + groupSuffix);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return config;
    }

    private <T> ConsumerFactory<String, T> jsonConsumerFactory(
            Class<T> targetType,
            ObjectMapper objectMapper,
            String groupSuffix) {

        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(targetType, objectMapper);
        jsonDeserializer.addTrustedPackages("*");
        jsonDeserializer.setUseTypeHeaders(false);

        ErrorHandlingDeserializer<T> valueDeserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                baseConfig(groupSuffix),
                new StringDeserializer(),
                valueDeserializer
        );
    }

    @Bean
    public ConsumerFactory<String, SummaryReadyEvent> summaryConsumerFactory(ObjectMapper kafkaObjectMapper) {
        return jsonConsumerFactory(SummaryReadyEvent.class, kafkaObjectMapper, "");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SummaryReadyEvent> summaryListenerFactory(
            ConsumerFactory<String, SummaryReadyEvent> summaryConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, SummaryReadyEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(summaryConsumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, NoteEvent> noteLifecycleConsumerFactory(ObjectMapper kafkaObjectMapper) {
        return jsonConsumerFactory(NoteEvent.class, kafkaObjectMapper, "-indexer");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NoteEvent> noteLifecycleListenerFactory(
            ConsumerFactory<String, NoteEvent> noteLifecycleConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, NoteEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(noteLifecycleConsumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, SummaryRequestedEvent> summaryRequestConsumerFactory(ObjectMapper kafkaObjectMapper) {
        return jsonConsumerFactory(SummaryRequestedEvent.class, kafkaObjectMapper, "-summarizer");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SummaryRequestedEvent> summaryRequestListenerFactory(
            ConsumerFactory<String, SummaryRequestedEvent> summaryRequestConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, SummaryRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(summaryRequestConsumerFactory);
        return factory;
    }
}
