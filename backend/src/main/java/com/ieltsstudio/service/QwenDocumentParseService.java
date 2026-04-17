package com.ieltsstudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QwenDocumentParseService {

    @Value("${qwen.api-key:}")
    private String apiKey;

    @Value("${qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${qwen.model:qwen-vl-plus}")
    private String model;

    @Value("${qwen.max-pages:20}")
    private int maxPages;

    @Value("${qwen.render-dpi:160}")
    private int renderDpi;

    @Value("${qwen.image-quality:0.82}")
    private float imageQuality;

    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String parseDocument(byte[] fileBytes, String filename) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Qwen API key 未配置，请在 application.yml 中设置 qwen.api-key");
        }
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return parsePdf(fileBytes);
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
            return parseImage(fileBytes, filename, 1);
        }
        throw new RuntimeException("Qwen 精准解析当前仅支持 PDF 或图片文件");
    }

    private String parsePdf(byte[] fileBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(fileBytes)))) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = Math.min(document.getNumberOfPages(), maxPages);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, renderDpi, ImageType.RGB);
                byte[] jpegBytes = toJpegBytes(image);
                String pageText = parseImage(jpegBytes, "page-" + (i + 1) + ".jpg", i + 1);
                if (!pageText.isBlank()) {
                    sb.append(pageText.trim()).append("\n\n");
                }
            }
            if (document.getNumberOfPages() > maxPages) {
                log.warn("Qwen precise parse truncated PDF pages from {} to {}", document.getNumberOfPages(), maxPages);
            }
            return sb.toString().trim();
        }
    }

    private String parseImage(byte[] imageBytes, String filename, int pageNumber) throws Exception {
        String mimeType = detectMimeType(filename);
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.1);
        requestBody.put("messages", List.of(
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of(
                                        "type", "text",
                                        "text", "请对这张试卷页面图片进行高精度转写。要求：1. 只输出可读文字内容，不要解释；2. 保留题号、段落、标题、选项顺序；3. 若有图表或图片，请尽可能转写其中可见文字，并用简短文字说明其结构；4. 不要添加原文中没有的答案。"
                                ),
                                Map.of(
                                        "type", "image_url",
                                        "image_url", Map.of("url", dataUrl)
                                )
                        )
                )
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Qwen parse failed HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
        log.info("Qwen parsed page {} with {} chars", pageNumber, content.length());
        return content;
    }

    private byte[] toJpegBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JPG writer not available");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, imageQuality)));
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private String detectMimeType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
}
