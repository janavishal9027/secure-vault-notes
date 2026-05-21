package com.application.notes.service;

import com.application.notes.configuration.KafkaTopics;
import com.application.notes.event.SummaryReadyEvent;
import com.application.notes.model.Notes;
import com.application.notes.repository.NotesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryEventConsumer {

    private final NotesRepository notesRepository;

    @KafkaListener(
            topics = KafkaTopics.AI_SUMMARY_READY,
            groupId = "${spring.kafka.consumer.group-id:notes-service}",
            containerFactory = "summaryListenerFactory"
    )
    public void onSummaryReady(SummaryReadyEvent event) {

        if (event == null || event.getNoteId() == null) {
            log.warn("received summary event with no noteId, skipping");
            return;
        }

        notesRepository.findById(event.getNoteId()).ifPresentOrElse(
                note -> applySummary(note, event),
                () -> log.warn("summary received for unknown note {}", event.getNoteId())
        );
    }

    private void applySummary(Notes note, SummaryReadyEvent event) {

        note.setSummary(event.getSummary());
        note.setSummaryStatus(event.getStatus());
        notesRepository.save(note);
        log.info("applied summary for note {} status={}", note.getNoteId(), event.getStatus());
    }
}
