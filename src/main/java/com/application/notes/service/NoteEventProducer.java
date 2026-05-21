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

        kafkaTemplate.send(KafkaTopics.NOTES_SUMMARY_REQUEST, event.getNoteId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("failed to publish summary-requested event for {}: {}", event.getNoteId(), ex.getMessage());
                    }
                });
    }

    private void publishLifecycle(NoteEvent event) {

        kafkaTemplate.send(KafkaTopics.NOTES_LIFECYCLE, event.getNoteId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("failed to publish note lifecycle event {} for {}: {}", event.getEventType(), event.getNoteId(), ex.getMessage());
                    }
                });
    }
}
