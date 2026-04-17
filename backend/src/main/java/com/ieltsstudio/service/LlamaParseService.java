package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaParseService {

    @Value("${llama-parse.api-key:}")
    private String apiKey;

    @Value("${llama-parse.base-url:https://api.cloud.llamaindex.ai/api/parsing}")
    private String baseUrl;

    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Upload PDF bytes to LlamaParse, poll for completion, return markdown text.
     * Blocks in the calling thread (call from @Async context only).
     */
    public String parseDocument(byte[] fileBytes, String filename) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("LlamaParse API key 未配置，请在 application.yml 中设置 llama-parse.api-key");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        String jobId = uploadFile(client, fileBytes, filename);
        log.info("LlamaParse job created: {}", jobId);

        // Poll up to 5 minutes (100 × 3 s)
        for (int i = 0; i < 100; i++) {
            Thread.sleep(3000);
            String status = getJobStatus(client, jobId);
            log.debug("LlamaParse job {} status: {}", jobId, status);
            if ("SUCCESS".equals(status)) {
                String markdown = getResult(client, jobId);
                log.info("LlamaParse job {} done, result length={}", jobId, markdown.length());
                return markdown;
            } else if ("ERROR".equals(status)) {
                throw new RuntimeException("LlamaParse job failed for jobId=" + jobId);
            }
        }
        throw new RuntimeException("LlamaParse job timed out (5 min) for jobId=" + jobId);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String uploadFile(HttpClient client, byte[] fileBytes, String filename) throws Exception {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String CRLF = "\r\n";

        // Build multipart body manually (no external deps)
        byte[] partHeader = (
                "--" + boundary + CRLF +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + CRLF +
                "Content-Type: application/octet-stream" + CRLF + CRLF
        ).getBytes(StandardCharsets.UTF_8);
        byte[] partFooter = (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[partHeader.length + fileBytes.length + partFooter.length];
        System.arraycopy(partHeader, 0, body, 0, partHeader.length);
        System.arraycopy(fileBytes, 0, body, partHeader.length, fileBytes.length);
        System.arraycopy(partFooter, 0, body, partHeader.length + fileBytes.length, partFooter.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/upload"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("LlamaParse upload failed HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode node = objectMapper.readTree(response.body());
        return node.path("id").asText();
    }

    private String getJobStatus(HttpClient client, String jobId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/job/" + jobId))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(response.body());
        return node.path("status").asText();
    }

    private String getResult(HttpClient client, String jobId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/job/" + jobId + "/result/markdown"))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("LlamaParse result fetch failed HTTP " + response.statusCode());
        }
        JsonNode node = objectMapper.readTree(response.body());
        return node.path("markdown").asText();
    }
}
