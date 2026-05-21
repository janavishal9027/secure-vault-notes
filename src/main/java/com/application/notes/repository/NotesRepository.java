package com.application.notes.repository;

import com.application.notes.model.Notes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotesRepository extends JpaRepository<Notes, String> {

    List<Notes> findByOwnerUserIdAndStatus(String ownerUserId, String status);

    List<Notes> findByOwnerUserIdAndArchivedFalseAndStatus(String ownerUserId, String status);

    List<Notes> findByOwnerUserIdAndArchivedTrueAndStatus(String ownerUserId, String status);

    List<Notes> findByOwnerUserIdAndPinnedTrueAndStatus(String ownerUserId, String status);

    Optional<Notes> findByNoteIdAndOwnerUserId(String noteId, String ownerUserId);
}
