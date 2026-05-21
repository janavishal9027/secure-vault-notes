package com.application.notes.service;

import com.application.notes.configuration.KafkaTopics;
import com.application.notes.event.NoteEvent;
import com.application.notes.feignService.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Listens on `notes.lifecycle` and asks ai-core-service to (re)index or delete
 * the corresponding vector embeddings. Replaces the indexer logic that
 * previously lived in AIGateway.
 *
 * Runs on the Kafka consumer thread pool, so blocking HTTP calls are fine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteIndexerConsumer {

    private final AiClient aiClient;

    @KafkaListener(
            topics = KafkaTopics.NOTES_LIFECYCLE,
            groupId = "${spring.kafka.consumer.group-id:notes-service}-indexer",
            containerFactory = "noteLifecycleListenerFactory"
    )
    public void onNoteEvent(NoteEvent event) {
        if (event == null || event.getEventType() == null || event.getNoteId() == null) {
            log.warn("received malformed note lifecycle event, skipping");
            return;
        }

        try {
            switch (event.getEventType()) {
                case CREATED, UPDATED -> index(event);
                case DELETED -> remove(event.getNoteId());
            }
        } catch (Exception ex) {
            log.warn("indexing {} for note {} failed: {}",
                    event.getEventType(), event.getNoteId(), ex.getMessage());
        }
    }

    private void index(NoteEvent event) {
        if (event.getContent() == null || event.getContent().isBlank()) {
            log.debug("skipping index for {} — no content", event.getNoteId());
            return;
        }
        aiClient.embed(event.getNoteId(), event.getOwnerUserId(), event.getTitle(), event.getContent());
        log.info("indexed note {}", event.getNoteId());
    }

    private void remove(String noteId) {
        aiClient.deleteEmbeddings(noteId);
        log.info("removed embeddings for note {}", noteId);
    }
}
