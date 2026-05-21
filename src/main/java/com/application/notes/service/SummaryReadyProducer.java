package com.application.notes.service;

import com.application.notes.configuration.KafkaTopics;
import com.application.notes.event.SummaryReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes ai.summary.ready events after the summarization call completes.
 * Used to be in AIGateway; lives in notes service now.
 *
 * SummaryEventConsumer (same service) picks the event up and writes the summary
 * back to the Notes table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryReadyProducer {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishReady(String noteId, String ownerUserId, String summary) {
        send(new SummaryReadyEvent(noteId, ownerUserId, summary, STATUS_READY, null));
    }

    public void publishFailed(String noteId, String ownerUserId, String errorMessage) {
        send(new SummaryReadyEvent(noteId, ownerUserId, null, STATUS_FAILED, errorMessage));
    }

    private void send(SummaryReadyEvent event) {
        kafkaTemplate.send(KafkaTopics.AI_SUMMARY_READY, event.getNoteId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("failed to publish summary event for {}: {}", event.getNoteId(), ex.getMessage());
                    }
                });
    }
}
