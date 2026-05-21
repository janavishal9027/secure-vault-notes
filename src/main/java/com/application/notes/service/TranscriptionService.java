package com.application.notes.service;

import com.application.notes.dtos.TranscriptionResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface TranscriptionService {
    TranscriptionResponseDto transcribe(MultipartFile audioFile, String language);
}
