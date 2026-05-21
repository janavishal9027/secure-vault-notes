package com.application.notes.service;

import com.application.notes.configuration.KafkaTopics;
import com.application.notes.event.SummaryRequestedEvent;
import com.application.notes.feignService.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Listens on `notes.summary.request`, calls ai-worker for the summary, and
 * publishes the result to `ai.summary.ready`. SummaryEventConsumer (in this
 * same service) consumes that event to persist the summary on the note.
 *
 * Replaces the orchestration that used to live in AIGateway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryRequestConsumer {

    private final AiClient aiClient;
    private final SummaryReadyProducer summaryReadyProducer;

    @KafkaListener(
            topics = KafkaTopics.NOTES_SUMMARY_REQUEST,
            groupId = "${spring.kafka.consumer.group-id:notes-service}-summarizer",
            containerFactory = "summaryRequestListenerFactory"
    )
    public void onSummaryRequest(SummaryRequestedEvent event) {
        if (event == null || event.getNoteId() == null) {
            log.warn("received summary request with no noteId, skipping");
            return;
        }
        if (event.getContent() == null || event.getContent().isBlank()) {
            log.debug("summary request for note {} has no content; skipping", event.getNoteId());
            return;
        }

        try {
            String summary = aiClient.summarize(event.getNoteId(), event.getTitle(), event.getContent());
            summaryReadyProducer.publishReady(event.getNoteId(), event.getOwnerUserId(), summary);
        } catch (Exception ex) {
            String reason = describeFailure(ex);
            log.warn("summarization failed for note {}: {}", event.getNoteId(), reason);
            summaryReadyProducer.publishFailed(event.getNoteId(), event.getOwnerUserId(), reason);
        }
    }

    private String describeFailure(Throwable ex) {
        if (ex instanceof HttpStatusCodeException hsce) {
            String body = hsce.getResponseBodyAsString();
            return hsce.getStatusCode() + (body == null || body.isBlank() ? "" : " - " + body);
        }
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
