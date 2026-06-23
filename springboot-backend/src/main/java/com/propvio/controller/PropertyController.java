package com.propvio.controller;

import com.propvio.dto.response.ApiResponse;
import com.propvio.model.Property;
import com.propvio.model.User;
import com.propvio.service.PropertyService;
import com.propvio.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;
    private final UserService userService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    @Value("${imagekit.private-key:}")
    private String imagekitPrivateKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Public reads ──────────────────────────────────────────────────────────

    @GetMapping("/api/product/list")
    public ResponseEntity<ApiResponse<?>> listProperties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Page<Property> result = propertyService.getActiveProperties(page, size);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "properties", result.getContent(),
            "total",      result.getTotalElements(),
            "pages",      result.getTotalPages(),
            "page",       result.getNumber()
        )));
    }

    @GetMapping("/api/product/single")
    public ResponseEntity<ApiResponse<?>> getSingle(@RequestParam Long id,
                                                     @AuthenticationPrincipal User currentUser) {
        Optional<Property> opt = propertyService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Property not found"));

        Property p = opt.get();
        if (currentUser != null) {
            userService.recordInteraction(
                currentUser.getId(), p.getId(),
                p.getLocation(), "view", p.getPrice(), p.getType()
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(p));
    }

    @PostMapping("/api/product/filter")
    public ResponseEntity<ApiResponse<?>> filter(@RequestBody Map<String, Object> filters) {
        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) filters.getOrDefault("type",
            List.of("flat", "house", "plot", "villa"));
        @SuppressWarnings("unchecked")
        List<String> availability = (List<String>) filters.getOrDefault("availability",
            List.of("buy", "rent"));
        int minBeds     = ((Number) filters.getOrDefault("minBeds",   0)).intValue();
        double minPrice = ((Number) filters.getOrDefault("minPrice",  0)).doubleValue();
        double maxPrice = ((Number) filters.getOrDefault("maxPrice",  Double.MAX_VALUE)).doubleValue();
        double minSqft  = ((Number) filters.getOrDefault("minSqft",   0)).doubleValue();
        int page        = ((Number) filters.getOrDefault("page", 0)).intValue();
        int size        = ((Number) filters.getOrDefault("size", 12)).intValue();

        Page<Property> result = propertyService.searchWithFilters(
            types, availability, minBeds, minPrice, maxPrice, minSqft, page, size
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "properties", result.getContent(),
            "total",      result.getTotalElements()
        )));
    }

    // ── User listing routes (auth required) ──────────────────────────────────

    @GetMapping("/api/user/properties")
    public ResponseEntity<ApiResponse<?>> myListings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(propertyService.getUserListings(user.getId())));
    }

    @PostMapping("/api/user/properties")
    public ResponseEntity<ApiResponse<?>> createListing(
            @RequestBody Map<String, Object> data,
            @AuthenticationPrincipal User user) {

        Property p = buildPropertyFromMap(data);
        p.setPostedBy(user.getId());
        p.setStatus("pending");
        p.setExpiresAt(LocalDateTime.now().plusDays(30));

        if (data.containsKey("imageUrls") && data.get("imageUrls") != null) {
            @SuppressWarnings("unchecked")
            List<String> urls = (List<String>) data.get("imageUrls");
            p.setImage(urls);
        }

        Property saved = propertyService.save(p);
        return ResponseEntity.ok(ApiResponse.ok("Listing submitted for review.", Map.of("id", saved.getId())));
    }

    @PutMapping("/api/user/properties/{id}")
    public ResponseEntity<ApiResponse<?>> updateListing(
            @PathVariable Long id,
            @RequestBody Map<String, Object> data,
            @AuthenticationPrincipal User user) {

        Optional<Property> opt = propertyService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Not found"));

        Property p = opt.get();
        if (!p.getPostedBy().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.fail("Not your listing"));
        }

        updatePropertyFromMap(p, data);
        p.setStatus("pending");
        Property updated = propertyService.save(p);
        return ResponseEntity.ok(ApiResponse.ok("Listing updated.", Map.of("id", updated.getId())));
    }

    @DeleteMapping("/api/user/properties/{id}")
    public ResponseEntity<ApiResponse<?>> deleteListing(@PathVariable Long id,
                                                          @AuthenticationPrincipal User user) {
        Optional<Property> opt = propertyService.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(ApiResponse.fail("Not found"));

        Property p = opt.get();
        if (!p.getPostedBy().equals(user.getId())) {
            return ResponseEntity.status(403).body(ApiResponse.fail("Not your listing"));
        }

        propertyService.delete(p);
        return ResponseEntity.ok(ApiResponse.ok("Listing deleted.", null));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @GetMapping("/api/products/list")
    public ResponseEntity<Map<String, Object>> listAllAdmin() {
        List<Property> all = propertyService.getAllProperties();
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("property", all);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/api/products/single/{id}")
    public ResponseEntity<Map<String, Object>> singleAdmin(@PathVariable Long id) {
        Map<String, Object> res = new HashMap<>();
        Optional<Property> opt = propertyService.findById(id);
        if (opt.isEmpty()) {
            res.put("success", false);
            res.put("message", "Property not found");
            return ResponseEntity.status(404).body(res);
        }
        res.put("success", true);
        res.put("property", opt.get());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/api/products/add")
    public ResponseEntity<Map<String, Object>> addAdmin(HttpServletRequest rawRequest) {
        Map<String, Object> res = new HashMap<>();
        try {
            MultipartHttpServletRequest req = (MultipartHttpServletRequest) rawRequest;
            Property p = new Property();
            p.setTitle(req.getParameter("title"));
            p.setLocation(req.getParameter("location"));
            p.setDescription(req.getParameter("description"));
            p.setType(req.getParameter("type"));
            p.setAvailability(req.getParameter("availability"));
            p.setPhone(req.getParameter("phone"));
            p.setGoogleMapLink(nvl(req.getParameter("googleMapLink")));
            p.setStatus("active");

            if (req.getParameter("price") != null)  p.setPrice(Double.parseDouble(req.getParameter("price")));
            if (req.getParameter("beds") != null)    p.setBeds(Integer.parseInt(req.getParameter("beds")));
            if (req.getParameter("baths") != null)   p.setBaths(Integer.parseInt(req.getParameter("baths")));
            if (req.getParameter("sqft") != null)    p.setSqft(Double.parseDouble(req.getParameter("sqft")));

            List<String> amenities = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String a = req.getParameter("amenities[" + i + "]");
                if (a == null) break;
                amenities.add(a);
            }
            p.setAmenities(amenities);
            p.setImage(saveUploadedImages(req));

            Property saved = propertyService.save(p);
            res.put("success", true);
            res.put("message", "Property added successfully");
            res.put("id", saved.getId());
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Failed to add property: " + e.getMessage());
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/api/products/update")
    public ResponseEntity<Map<String, Object>> updateAdmin(HttpServletRequest rawRequest) {
        Map<String, Object> res = new HashMap<>();
        try {
            MultipartHttpServletRequest req = (MultipartHttpServletRequest) rawRequest;
            String idStr = req.getParameter("id");
            if (idStr == null) {
                res.put("success", false);
                res.put("message", "id is required");
                return ResponseEntity.badRequest().body(res);
            }
            Long id = Long.parseLong(idStr);
            Optional<Property> opt = propertyService.findById(id);
            if (opt.isEmpty()) {
                res.put("success", false);
                res.put("message", "Property not found");
                return ResponseEntity.status(404).body(res);
            }

            Property p = opt.get();
            if (req.getParameter("title") != null)         p.setTitle(req.getParameter("title"));
            if (req.getParameter("location") != null)      p.setLocation(req.getParameter("location"));
            if (req.getParameter("description") != null)   p.setDescription(req.getParameter("description"));
            if (req.getParameter("type") != null)          p.setType(req.getParameter("type"));
            if (req.getParameter("availability") != null)  p.setAvailability(req.getParameter("availability"));
            if (req.getParameter("phone") != null)         p.setPhone(req.getParameter("phone"));
            if (req.getParameter("googleMapLink") != null) p.setGoogleMapLink(req.getParameter("googleMapLink"));
            if (req.getParameter("price") != null)         p.setPrice(Double.parseDouble(req.getParameter("price")));
            if (req.getParameter("beds") != null)          p.setBeds(Integer.parseInt(req.getParameter("beds")));
            if (req.getParameter("baths") != null)         p.setBaths(Integer.parseInt(req.getParameter("baths")));
            if (req.getParameter("sqft") != null)          p.setSqft(Double.parseDouble(req.getParameter("sqft")));

            List<String> amenities = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String a = req.getParameter("amenities[" + i + "]");
                if (a == null) break;
                amenities.add(a);
            }
            if (!amenities.isEmpty()) p.setAmenities(amenities);

            List<String> newImages = saveUploadedImages(req);
            if (!newImages.isEmpty()) p.setImage(newImages);

            propertyService.save(p);
            res.put("success", true);
            res.put("message", "Property updated successfully");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Failed to update property: " + e.getMessage());
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/api/products/remove")
    public ResponseEntity<Map<String, Object>> removeAdmin(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            Long id = Long.parseLong(body.get("id").toString());
            propertyService.findById(id).ifPresent(propertyService::delete);
            res.put("success", true);
            res.put("message", "Property removed");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "Failed to remove property");
        }
        return ResponseEntity.ok(res);
    }

    // ── User image upload endpoint ────────────────────────────────────────────

    // POST /api/user/upload-images — authenticated users upload images before submitting a listing
    @PostMapping("/api/user/upload-images")
    public ResponseEntity<ApiResponse<?>> uploadImagesForUser(HttpServletRequest rawRequest) {
        List<String> urls = new ArrayList<>();
        try {
            MultipartHttpServletRequest req = (MultipartHttpServletRequest) rawRequest;
            for (int i = 1; i <= 4; i++) {
                MultipartFile file = req.getFile("image" + i);
                if (file == null || file.isEmpty()) continue;
                String url = uploadToImageKit(file);
                if (url != null) urls.add(url);
            }
        } catch (Exception e) {
            System.err.println("[uploadImagesForUser] Error: " + e.getMessage());
            return ResponseEntity.status(500).body(ApiResponse.fail("Image upload failed: " + e.getMessage()));
        }
        return ResponseEntity.ok(ApiResponse.ok(urls));
    }

    // ── Image upload ──────────────────────────────────────────────────────────

    private List<String> saveUploadedImages(MultipartHttpServletRequest req) throws IOException {
        List<String> urls = new ArrayList<>();
        boolean useImageKit = imagekitPrivateKey != null && !imagekitPrivateKey.isBlank();

        for (int i = 1; i <= 10; i++) {
            MultipartFile file = req.getFile("image" + i);
            if (file == null || file.isEmpty()) continue;

            if (useImageKit) {
                String url = uploadToImageKit(file);
                if (url != null) {
                    urls.add(url);
                }
            } else {
                // Fallback: save to local filesystem
                String ext = fileExtension(file.getOriginalFilename());
                String filename = UUID.randomUUID() + ext;
                Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
                Files.createDirectories(dir);
                Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                urls.add(backendUrl + "/uploads/" + filename);
            }
        }
        return urls;
    }

    @SuppressWarnings("unchecked")
    private String uploadToImageKit(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String mimeType  = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String filename  = UUID.randomUUID() + fileExtension(file.getOriginalFilename());
            String base64    = Base64.getEncoder().encodeToString(bytes);
            String dataUri   = "data:" + mimeType + ";base64," + base64;

            // ImageKit accepts base64 data-URI as the "file" field — no binary multipart needed
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", dataUri);
            body.add("fileName", filename);
            body.add("folder", "/properties");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            String auth = Base64.getEncoder().encodeToString((imagekitPrivateKey + ":").getBytes());
            headers.set("Authorization", "Basic " + auth);

            System.out.println("[ImageKit] Uploading " + filename + " (" + bytes.length + " bytes)…");

            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://upload.imagekit.io/api/v1/files/upload",
                new HttpEntity<>(body, headers),
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String url = (String) response.getBody().get("url");
                System.out.println("[ImageKit] Success → " + url);
                return url;
            }
            System.err.println("[ImageKit] Unexpected status: " + response.getStatusCode());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("[ImageKit] HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("[ImageKit] Upload error: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return ".jpg";
    }

    private Property buildPropertyFromMap(Map<String, Object> d) {
        Property p = new Property();
        updatePropertyFromMap(p, d);
        return p;
    }

    @SuppressWarnings("unchecked")
    private void updatePropertyFromMap(Property p, Map<String, Object> d) {
        if (d.containsKey("title") && d.get("title") != null)               p.setTitle((String) d.get("title"));
        if (d.containsKey("location") && d.get("location") != null)         p.setLocation((String) d.get("location"));
        if (d.containsKey("price") && d.get("price") != null)               p.setPrice(((Number) d.get("price")).doubleValue());
        if (d.containsKey("beds") && d.get("beds") != null)                 p.setBeds(((Number) d.get("beds")).intValue());
        if (d.containsKey("baths") && d.get("baths") != null)               p.setBaths(((Number) d.get("baths")).intValue());
        if (d.containsKey("sqft") && d.get("sqft") != null)                 p.setSqft(((Number) d.get("sqft")).doubleValue());
        if (d.containsKey("type") && d.get("type") != null)                 p.setType((String) d.get("type"));
        if (d.containsKey("availability") && d.get("availability") != null) p.setAvailability((String) d.get("availability"));
        if (d.containsKey("description") && d.get("description") != null)   p.setDescription((String) d.get("description"));
        if (d.containsKey("amenities") && d.get("amenities") != null)       p.setAmenities((List<String>) d.get("amenities"));
        if (d.containsKey("phone") && d.get("phone") != null)               p.setPhone((String) d.get("phone"));
        if (d.containsKey("googleMapLink"))                                   p.setGoogleMapLink(d.get("googleMapLink") != null ? (String) d.get("googleMapLink") : "");
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
