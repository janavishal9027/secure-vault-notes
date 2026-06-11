package com.application.notes.feignService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP client used by the notes service to talk directly to the Python AI
 * services. Replaces the old AIGateway hop.
 *
 * <ul>
 *   <li>ai-core-service (default port 8001): embeddings, search, recommendations,
 *       chat, and summarisation (merged in from the former standalone
 *       ai-worker service).</li>
 * </ul>
 *
 * Only the methods invoked from Kafka consumers live here today; add new ones
 * as needed.
 */
@Slf4j
@Service
public class AiClient {

    private final RestTemplate restTemplate;

    @Value("${ai.core.base-url:http://localhost:8001}")
    private String aiCoreBaseUrl;

    public AiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void embed(String noteId, String ownerUserId, String title, String content) {
        Map<String, Object> body = new HashMap<>();
        body.put("noteId", noteId);
        body.put("ownerUserId", ownerUserId);
        body.put("title", title);
        body.put("content", content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.postForEntity(
                aiCoreBaseUrl + "/embed",
                new HttpEntity<>(body, headers),
                Map.class
        );
    }

    public void deleteEmbeddings(String noteId) {
        restTemplate.delete(aiCoreBaseUrl + "/embed/{noteId}", noteId);
    }

    /**
     * Calls ai-core-service for summarisation. Returns the summary text.
     * Throws on transport or upstream failure; let the caller decide whether to
     * publish a FAILED summary event.
     */
    public String summarize(String noteId, String title, String content) {
        Map<String, Object> body = new HashMap<>();
        body.put("noteId", noteId);
        body.put("title", title);
        body.put("content", content);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<?, ?> response = restTemplate.postForObject(
                aiCoreBaseUrl + "/summarize",
                new HttpEntity<>(body, headers),
                Map.class
        );
        if (response == null) {
            throw new IllegalStateException("ai-core returned empty response");
        }
        Object summary = response.get("summary");
        if (summary == null) {
            throw new IllegalStateException("ai-core response missing summary field");
        }
        return summary.toString();
    }
}
