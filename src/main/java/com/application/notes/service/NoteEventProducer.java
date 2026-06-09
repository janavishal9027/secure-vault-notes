package com.application.notes.service;

import com.application.notes.configuration.KafkaTopics;
import com.application.notes.event.NoteEvent;
import com.application.notes.event.SummaryRequestedEvent;
import com.application.notes.model.Notes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCreated(Notes note) {

        publishLifecycle(NoteEvent.builder()
                .eventType(NoteEvent.Type.CREATED)
                .noteId(note.getNoteId())
                .ownerUserId(note.getOwnerUserId())
                .title(note.getTitle())
                .content(note.getContent())
                .occurredAt(Instant.now())
                .build());
    }

    public void publishUpdated(Notes note) {

        publishLifecycle(NoteEvent.builder()
                .eventType(NoteEvent.Type.UPDATED)
                .noteId(note.getNoteId())
                .ownerUserId(note.getOwnerUserId())
                .title(note.getTitle())
                .content(note.getContent())
                .occurredAt(Instant.now())
                .build());
    }

    public void publishDeleted(String noteId, String ownerUserId) {

        publishLifecycle(NoteEvent.builder()
                .eventType(NoteEvent.Type.DELETED)
                .noteId(noteId)
                .ownerUserId(ownerUserId)
                .occurredAt(Instant.now())
                .build());
    }

    public void publishSummaryRequested(Notes note) {

        SummaryRequestedEvent event = SummaryRequestedEvent.builder()
                .noteId(note.getNoteId())
                .ownerUserId(note.getOwnerUserId())
                .title(note.getTitle())
                .content(note.getContent())
                .occurredAt(Instant.now())
                .build();

        safeSend(KafkaTopics.NOTES_SUMMARY_REQUEST, event.getNoteId(), event,
                "summary-requested event");
    }

    private void publishLifecycle(NoteEvent event) {

        safeSend(KafkaTopics.NOTES_LIFECYCLE, event.getNoteId(), event,
                "note lifecycle event " + event.getEventType());
    }

    /**
     * Publishes an event without ever letting a broker problem fail the caller.
     *
     * These events are fire-and-forget indexing/summary signals. {@code kafkaTemplate.send()}
     * blocks up to {@code max.block.ms} (5s) fetching producer metadata and throws
     * SYNCHRONOUSLY — wrapped by Spring as {@code KafkaException("Send failed")} — when the
     * broker is unreachable. Left uncaught, that turns an ordinary note save/update/delete
     * into a 500. We catch it here so the user's note operation always succeeds; the event is
     * simply dropped (and logged) until the broker is reachable again. The {@code whenComplete}
     * callback still handles the async (post-buffer) delivery failures.
     */
    private void safeSend(String topic, String key, Object event, String description) {
        try {
            kafkaTemplate.send(topic, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("async failure publishing {} for {}: {}", description, key, ex.getMessage());
                        }
                    });
        } catch (Exception ex) {
            log.error("failed to publish {} for {} (broker unreachable?): {}", description, key, ex.getMessage());
        }
    }
}
