package com.application.notes.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoteEvent {

    public enum Type { CREATED, UPDATED, DELETED }

    private Type eventType;
    private String noteId;
    private String ownerUserId;
    private String title;
    private String content;
    private Instant occurredAt;
}
