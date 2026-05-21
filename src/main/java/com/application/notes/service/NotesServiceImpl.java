package com.application.notes.service;

import com.application.notes.Utils.Constants;
import com.application.notes.dtos.NotesRequestDto;
import com.application.notes.dtos.NotesResponseDto;
import com.application.notes.exceptions.AccessDeniedException;
import com.application.notes.exceptions.ResourceNotFoundException;
import com.application.notes.model.Notes;
import com.application.notes.repository.NotesRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class NotesServiceImpl implements NotesService {

    private static final String SUMMARY_STATUS_PENDING = "PENDING";
    private static final String SUMMARY_STATUS_NONE = "NONE";
    private static final int SUMMARY_MIN_CONTENT_CHARS = 50;

    @Autowired
    private NotesRepository notesRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private NoteEventProducer noteEventProducer;


    @Override
    public NotesResponseDto creatingNotes(NotesRequestDto noteRequestDto, String ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        Notes notes = Notes.builder()
                .noteId("NOTE" + now.getYear() + now.getDayOfYear() + now.getSecond() + now.getNano())
                .title(noteRequestDto.getTitle())
                .content(noteRequestDto.getContent())
                .ownerUserId(ownerUserId)
                .pinned(false)
                .archived(false)
                .tags(noteRequestDto.getTags())
                .status("ACTIVE")
                .summaryStatus(SUMMARY_STATUS_NONE)
                .build();

        Notes saved = notesRepository.save(notes);
        noteEventProducer.publishCreated(saved);
        return modelMapper.map(saved, NotesResponseDto.class);
    }

    @Override
    public NotesResponseDto getNoteById(String noteId, String ownerUserId) {
        Notes notes = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId).orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));
        return modelMapper.map(notes, NotesResponseDto.class);
    }

    @Override
    public List<NotesResponseDto> getAllActiveNotesByOwnerUserId(String ownerUserId) {
        return notesRepository.findByOwnerUserIdAndArchivedFalseAndStatus(ownerUserId, Constants.ACTIVE.name())
                .stream()
                .map(notes -> modelMapper.map(notes, NotesResponseDto.class))
                .toList();
    }

    @Override
    public List<NotesResponseDto> getPinnedNotesByOwner(String ownerUserId) {
        return notesRepository.findByOwnerUserIdAndPinnedTrueAndStatus(ownerUserId, Constants.ACTIVE.name())
                .stream()
                .map(notes -> modelMapper.map(notes, NotesResponseDto.class))
                .toList();
    }

    @Override
    public List<NotesResponseDto> getArchivedNotesByOwner(String ownerUserId) {
        return notesRepository.findByOwnerUserIdAndArchivedTrueAndStatus(ownerUserId, Constants.ACTIVE.name())
                .stream()
                .map(notes -> modelMapper.map(notes, NotesResponseDto.class))
                .toList();
    }

    @Override
    public NotesResponseDto updateNote(String noteId, String ownerUserId, NotesRequestDto updatedNote) {
        Notes existingNote = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));

        if (!existingNote.getOwnerUserId().equals(ownerUserId)) {
            throw new AccessDeniedException("Note not found for the given owner user id: " + ownerUserId);
        }

        boolean contentChanged = !Objects.equals(existingNote.getContent(), updatedNote.getContent());

        existingNote.setTitle(updatedNote.getTitle());
        existingNote.setContent(updatedNote.getContent());
        existingNote.setPinned(Boolean.TRUE.equals(updatedNote.getPinned()));
        existingNote.setArchived(Boolean.TRUE.equals(updatedNote.getArchived()));
        existingNote.setTags(updatedNote.getTags());

        Notes saved = notesRepository.save(existingNote);
        if (contentChanged) {
            noteEventProducer.publishUpdated(saved);
        }
        return modelMapper.map(saved, NotesResponseDto.class);
    }

    @Override
    public NotesResponseDto updateNoteUsingPartialFields(String noteId, String ownerUserId, NotesRequestDto notesRequestDto) {
        Notes existingNote = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId).orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));
        if(!existingNote.getOwnerUserId().equals(ownerUserId)) {
            throw new AccessDeniedException("Note not found for the given owner user id: " + ownerUserId);
        }

        boolean contentChanged = false;
        if(notesRequestDto.getTitle() != null) {
            existingNote.setTitle(notesRequestDto.getTitle());
        }
        if(notesRequestDto.getContent() != null && !Objects.equals(existingNote.getContent(), notesRequestDto.getContent())) {
            existingNote.setContent(notesRequestDto.getContent());
            contentChanged = true;
        }
        if (notesRequestDto.getPinned() != null) {
            existingNote.setPinned(notesRequestDto.getPinned());
        }
        if (notesRequestDto.getArchived() != null) {
            existingNote.setArchived(notesRequestDto.getArchived());
        }
        if (notesRequestDto.getTags() != null) {
            existingNote.setTags(notesRequestDto.getTags());
        }

        Notes saved = notesRepository.save(existingNote);
        if (contentChanged) {
            noteEventProducer.publishUpdated(saved);
        }
        return modelMapper.map(saved, NotesResponseDto.class);
    }

    @Override
    public NotesResponseDto pinNotes(String noteId, String ownerUserId) {
        Notes existingNote = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId).orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));
        existingNote.setPinned(true);
        return modelMapper.map(notesRepository.save(existingNote), NotesResponseDto.class);
    }

    @Override
    public NotesResponseDto unpinNotes(String noteId, String ownerUserId) {
        Notes notes = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId).orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));
        notes.setPinned(false);
        return modelMapper.map(notesRepository.save(notes), NotesResponseDto.class);
    }

    @Override
    public NotesResponseDto archiveNote(String noteId, String ownerUserId) {
        Notes notes = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId).orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));
        notes.setArchived(true);
        return modelMapper.map(notesRepository.save(notes), NotesResponseDto.class);
    }

    @Override
    public NotesResponseDto unarchiveNote(String noteId, String ownerUserId) {
        Notes notes = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId).orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));
        notes.setArchived(false);
        return modelMapper.map(notesRepository.save(notes), NotesResponseDto.class);
    }

    @Override
    public void deleteNote(String noteId, String ownerUserId) {
        Notes existingNote = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));

        if (!existingNote.getOwnerUserId().equals(ownerUserId)) {
            throw new AccessDeniedException("Note not found for the given owner user id: " + ownerUserId);
        }
        notesRepository.delete(existingNote);
        noteEventProducer.publishDeleted(noteId, ownerUserId);
    }

    @Override
    public NotesResponseDto requestSummary(String noteId, String ownerUserId) {
        Notes note = notesRepository.findByNoteIdAndOwnerUserId(noteId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found with id: " + noteId + " for user id: " + ownerUserId));

        if (note.getContent() == null || note.getContent().trim().length() < SUMMARY_MIN_CONTENT_CHARS) {
            throw new IllegalArgumentException("Note content must be at least " + SUMMARY_MIN_CONTENT_CHARS + " characters to summarize");
        }

        if (SUMMARY_STATUS_PENDING.equals(note.getSummaryStatus())) {
            return modelMapper.map(note, NotesResponseDto.class);
        }

        note.setSummaryStatus(SUMMARY_STATUS_PENDING);
        Notes saved = notesRepository.save(note);

        noteEventProducer.publishSummaryRequested(saved);

        return modelMapper.map(saved, NotesResponseDto.class);
    }

}
