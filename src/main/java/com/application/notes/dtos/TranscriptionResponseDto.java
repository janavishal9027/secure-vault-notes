package com.application.notes.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TranscriptionResponseDto {
    private String text;
    private String language;
    private Double durationSeconds;
}
