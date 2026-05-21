package com.application.notes.service;

import com.application.notes.dtos.TranscriptionResponseDto;
import com.application.notes.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class TranscriptionServiceImpl implements TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionServiceImpl.class);

    private final RestTemplate restTemplate;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.transcription.url}")
    private String transcriptionUrl;

    @Value("${openai.transcription.model}")
    private String transcriptionModel;

    @Value("${openai.transcription.max-file-size-mb:25}")
    private long maxFileSizeMb;

    public TranscriptionServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public TranscriptionResponseDto transcribe(MultipartFile audioFile, String language) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required");
        }
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalStateException(
                "OpenAI API key is not configured. Set OPENAI_API_KEY environment variable."
            );
        }

        long sizeMb = audioFile.getSize() / (1024 * 1024);
        if (sizeMb > maxFileSizeMb) {
            throw new IllegalArgumentException(
                "Audio file exceeds the " + maxFileSizeMb + " MB limit"
            );
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(openAiApiKey);

            String filename = StringUtils.hasText(audioFile.getOriginalFilename())
                    ? audioFile.getOriginalFilename()
                    : "audio.webm";

            Resource fileResource = new ByteArrayResource(audioFile.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);
            body.add("model", transcriptionModel);
            body.add("response_format", "verbose_json");
            if (StringUtils.hasText(language)) {
                body.add("language", language);
            }

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(transcriptionUrl, request, Map.class);

            Map<String, Object> payload = response.getBody();
            if (payload == null || !payload.containsKey("text")) {
                throw new ResourceNotFoundException("Transcription response was empty");
            }

            String text = String.valueOf(payload.get("text"));
            String detectedLanguage = payload.containsKey("language")
                    ? String.valueOf(payload.get("language"))
                    : null;
            Double duration = payload.get("duration") instanceof Number
                    ? ((Number) payload.get("duration")).doubleValue()
                    : null;

            return TranscriptionResponseDto.builder()
                    .text(text)
                    .language(detectedLanguage)
                    .durationSeconds(duration)
                    .build();

        } catch (HttpStatusCodeException ex) {
            log.error("OpenAI transcription failed: {} - {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException(
                "Transcription provider rejected the request: " + ex.getStatusCode()
            );
        } catch (IOException ex) {
            log.error("Unable to read uploaded audio file", ex);
            throw new IllegalArgumentException("Unable to read uploaded audio file");
        }
    }
}
