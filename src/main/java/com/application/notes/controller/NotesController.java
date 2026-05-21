package com.application.notes.controller;

import com.application.notes.Utils.ApiResponse;
import com.application.notes.Utils.Constants;
import com.application.notes.dtos.NotesRequestDto;
import com.application.notes.dtos.NotesResponseDto;
import com.application.notes.dtos.TranscriptionResponseDto;
import com.application.notes.service.NotesService;
import com.application.notes.service.TranscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/notes")
public class NotesController {

    @Autowired
    private NotesService notesService;

    @Autowired
    private TranscriptionService transcriptionService;

    @PostMapping("/createdNote")
    public ResponseEntity<ApiResponse> createNote(@RequestBody NotesRequestDto notesRequestDto, HttpServletRequest request) {
        NotesResponseDto createdNote = notesService.creatingNotes(notesRequestDto, (String) request.getAttribute("ownerUserId"));
        return ResponseEntity.ok(new ApiResponse(Constants.SUCCESS.name(), createdNote, "Note created successfully"));
    }

    @GetMapping("/getNotes/{noteId}")
    public ResponseEntity<ApiResponse> gettingNotesById(@PathVariable String noteId, HttpServletRequest request) {
        NotesResponseDto getNotes = notesService.getNoteById(noteId, (String) request.getAttribute("ownerUserId"));
        return ResponseEntity.ok(new ApiResponse(Constants.SUCCESS.name(), getNotes, "Note retrieved successfully"));
    }

    @GetMapping("/owner/allNotes")
    public ResponseEntity<ApiResponse> gettingAllNotesByUserId(HttpServletRequest request) {
        return ResponseEntity.ok(new ApiResponse(
                Constants.SUCCESS.name(),
                notesService.getAllActiveNotesByOwnerUserId((String) request.getAttribute("ownerUserId")),
                "Non-Archived Notes retrieved successfully")
        );
    }

    @GetMapping("/owner/pinned")
    public ResponseEntity<ApiResponse> getPinnedNotesByOwner(HttpServletRequest request) {
        return ResponseEntity.ok(new ApiResponse(
                Constants.SUCCESS.name(),
                notesService.getPinnedNotesByOwner((String) request.getAttribute("ownerUserId")),
                "Pinned notes retrieved successfully")
        );
    }

    @GetMapping("/owner/archived")
    public ResponseEntity<ApiResponse> getArchivedNotesByOwner(HttpServletRequest request) {
        return ResponseEntity.ok(new ApiResponse(
                Constants.SUCCESS.name(),
                notesService.getArchivedNotesByOwner((String) request.getAttribute("ownerUserId")),
                "Archived notes retrieved successfully")
        );
    }
    
    @PutMapping("/updateNote")
    public ResponseEntity<ApiResponse> updateNotes(
            @RequestParam String noteId,
            @RequestBody NotesRequestDto notesRequestDto,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        notesService.updateNote(noteId, (String) request.getAttribute("ownerUserId"), notesRequestDto),
                        "Note updated successfully"
                )
        );
    }

    @PatchMapping("/updateNotePartial")
    public ResponseEntity<ApiResponse> updateNoteUsingPartialFields(
            @RequestParam String noteId,
            @RequestBody NotesRequestDto notesRequestDto,
            HttpServletRequest request
    ) {
        return new ResponseEntity<>(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        notesService.updateNoteUsingPartialFields(noteId, (String) request.getAttribute("ownerUserId"), notesRequestDto),
                        "Note updated successfully"
                ), HttpStatus.OK
        );
    }

    @PatchMapping("/pinNote")
    public ResponseEntity<ApiResponse> pinnedNotes(@RequestParam String noteId, HttpServletRequest request) {
        return new ResponseEntity<>(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        notesService.pinNotes(noteId, (String) request.getAttribute("ownerUserId")),
                        "Note pinned successfully"
                ), HttpStatus.OK
        );
    }

    @PatchMapping("/unpinNote")
    public ResponseEntity<ApiResponse> unpinNotes(@RequestParam String noteId, HttpServletRequest request) {
        return new ResponseEntity<>(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        notesService.unpinNotes(noteId, (String) request.getAttribute("ownerUserId")),
                        "Note unpinned successfully"
                ), HttpStatus.OK
        );
    }

    @PatchMapping("/archiveNote")
    public ResponseEntity<ApiResponse> archiveNote(@RequestParam String noteId, HttpServletRequest request) {
        return new ResponseEntity<>(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        notesService.archiveNote(noteId, (String) request.getAttribute("ownerUserId")),
                        "Note archived successfully"
                ), HttpStatus.OK
        );
    }

    @PatchMapping("/unarchiveNote")
    public ResponseEntity<ApiResponse> unarchiveNote(@RequestParam String noteId, HttpServletRequest request) {
        return new ResponseEntity<>(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        notesService.unarchiveNote(noteId, (String) request.getAttribute("ownerUserId")),
                        "Note unarchived successfully"
                ), HttpStatus.OK
        );
    }

    @DeleteMapping("/deleteNote")
    public ResponseEntity<ApiResponse> deleteNote(@RequestParam String noteId, HttpServletRequest request) {
        notesService.deleteNote(noteId, (String) request.getAttribute("ownerUserId"));
        return new ResponseEntity<>(
                new ApiResponse(
                        Constants.SUCCESS.name(),
                        "Note deleted successfully"
                ), HttpStatus.OK
        );
    }

    @PostMapping("/summarize/{noteId}")
    public ResponseEntity<ApiResponse> requestSummary(@PathVariable String noteId, HttpServletRequest request) {
        NotesResponseDto note = notesService.requestSummary(noteId, (String) request.getAttribute("ownerUserId"));
        return ResponseEntity.ok(new ApiResponse(Constants.SUCCESS.name(), note, "Summary generation requested"));
    }

    @PostMapping(value = "/transcribe", consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse> transcribeAudio(
            @RequestPart("file") MultipartFile audioFile,
            @RequestParam(value = "language", required = false) String language) {
        TranscriptionResponseDto result = transcriptionService.transcribe(audioFile, language);
        return ResponseEntity.ok(
                new ApiResponse(Constants.SUCCESS.name(), result, "Audio transcribed successfully")
        );
    }

}
