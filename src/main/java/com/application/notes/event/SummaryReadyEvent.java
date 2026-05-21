package com.application.notes.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummaryReadyEvent {

    private String noteId;
    private String ownerUserId;
    private String summary;
    private String status;
    private String errorMessage;
}
