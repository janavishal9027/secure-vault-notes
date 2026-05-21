package com.application.notes.service;

import com.application.notes.dtos.NotesRequestDto;
import com.application.notes.dtos.NotesResponseDto;
import com.application.notes.model.Notes;

import java.util.List;

public interface NotesService {

    NotesResponseDto creatingNotes(NotesRequestDto noteRequestDto, String ownerUserId);

    NotesResponseDto getNoteById(String noteId, String ownerUserId);

    List<NotesResponseDto> getAllActiveNotesByOwnerUserId(String ownerUserId);

    List<NotesResponseDto> getPinnedNotesByOwner(String ownerUserId);

    List<NotesResponseDto> getArchivedNotesByOwner(String ownerUserId);

    NotesResponseDto updateNote(String noteId, String ownerUserId, NotesRequestDto updatedNote);

    NotesResponseDto updateNoteUsingPartialFields(String noteId, String ownerUserId, NotesRequestDto notesRequestDto);

    NotesResponseDto pinNotes(String noteId, String ownerUserId);

    NotesResponseDto unpinNotes(String noteId, String ownerUserId);

    NotesResponseDto archiveNote(String noteId, String ownerUserId);

    NotesResponseDto unarchiveNote(String noteId, String ownerUserId);

    void deleteNote(String noteId, String ownerUserId);

    NotesResponseDto requestSummary(String noteId, String ownerUserId);

}
