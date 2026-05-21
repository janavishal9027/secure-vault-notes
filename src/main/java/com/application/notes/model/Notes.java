package com.application.notes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notes {

    @Id
    private String noteId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String ownerUserId;

    @Column(nullable = false)
    private boolean pinned = false;

    @Column(nullable = false)
    private boolean archived = false;

    private String tags;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 16)
    private String summaryStatus;

}
