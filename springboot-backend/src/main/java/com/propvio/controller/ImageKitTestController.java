package com.propvio.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary diagnostic endpoint — remove after confirming ImageKit uploads work.
 *
 * Test 1 (no file needed):
 *   GET http://localhost:8080/api/test/imagekit-ping
 *   Shows key length, endpoint, and whether a tiny auto-generated image uploads OK.
 *
 * Test 2 (upload your own file):
 *   POST http://localhost:8080/api/test/imagekit-upload
 *   multipart/form-data   field name: file
 */
@RestController
@RequestMapping("/api/test")
public class ImageKitTestController {

    @Value("${imagekit.private-key:}")
    private String privateKey;

    @Value("${imagekit.url-endpoint:}")
    private String urlEndpoint;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Test 1: automatic ping with a tiny generated image ───────────────────

    @GetMapping("/imagekit-ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> result = new HashMap<>();
        result.put("privateKeyLength", privateKey != null ? privateKey.length() : 0);
        result.put("privateKeyPrefix", privateKey != null && privateKey.length() > 10
                ? privateKey.substring(0, 10) + "…"
                : privateKey);
        result.put("urlEndpoint", urlEndpoint);

        if (privateKey == null || privateKey.isBlank()) {
            result.put("status", "ERROR");
            result.put("error", "imagekit.private-key is empty — check IMAGEKIT_PRIVATE_KEY env var");
            return ResponseEntity.ok(result);
        }

        // Minimal 1x1 white PNG (68 bytes, hardcoded)
        byte[] tinyPng = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI6QAAAABJRU5ErkJggg=="
        );

        Map<String, Object> uploadResult = doUpload(tinyPng, "image/png", "ping-test-" + UUID.randomUUID() + ".png");
        result.putAll(uploadResult);
        return ResponseEntity.ok(result);
    }

    // ── Test 2: upload a real file from the request ───────────────────────────

    @PostMapping("/imagekit-upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        result.put("originalFilename", file.getOriginalFilename());
        result.put("contentType", file.getContentType());
        result.put("sizeBytes", file.getSize());
        result.put("privateKeyLength", privateKey != null ? privateKey.length() : 0);

        if (privateKey == null || privateKey.isBlank()) {
            result.put("status", "ERROR");
            result.put("error", "imagekit.private-key is empty");
            return ResponseEntity.ok(result);
        }

        try {
            byte[] bytes = file.getBytes();
            String mime = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) ext = orig.substring(orig.lastIndexOf('.'));
            String filename = "test-" + UUID.randomUUID() + ext;

            Map<String, Object> uploadResult = doUpload(bytes, mime, filename);
            result.putAll(uploadResult);
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    // ── Shared upload logic ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> doUpload(byte[] bytes, String mimeType, String filename) {
        Map<String, Object> result = new HashMap<>();
        try {
            String base64  = Base64.getEncoder().encodeToString(bytes);
            String dataUri = "data:" + mimeType + ";base64," + base64;

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file",     dataUri);
            body.add("fileName", filename);
            body.add("folder",   "/properties");

            String auth = Base64.getEncoder().encodeToString((privateKey + ":").getBytes());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Basic " + auth);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://upload.imagekit.io/api/v1/files/upload",
                new HttpEntity<>(body, headers),
                Map.class
            );

            result.put("httpStatus", response.getStatusCodeValue());
            result.put("responseBody", response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                result.put("status", "SUCCESS");
                result.put("imageKitUrl", response.getBody().get("url"));
                result.put("fileId", response.getBody().get("fileId"));
            } else {
                result.put("status", "ERROR");
            }

        } catch (HttpClientErrorException e) {
            result.put("status", "ERROR");
            result.put("httpStatus", e.getStatusCode().value());
            result.put("errorBody", e.getResponseBodyAsString());
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("exceptionType", e.getClass().getName());
            result.put("error", e.getMessage());
        }
        return result;
    }
}
