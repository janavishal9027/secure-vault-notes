package com.application.notes.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotesResponseDto {

    private String noteId;
    private String title;
    private String content;
    private String ownerUserId;
    private boolean pinned;
    private boolean archived;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;
    private String summary;
    private String summaryStatus;
}
