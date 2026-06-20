package com.propvio.controller;

import com.propvio.dto.response.ApiResponse;
import com.propvio.model.Property;
import com.propvio.model.User;
import com.propvio.service.PropertyService;
import com.propvio.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;
    private final UserService userService;

    // ── Public reads ──────────────────────────────────────────────────────────

    // GET /api/product/list
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

    // GET /api/product/single?id=xxx
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

    // POST /api/product/filter
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
            @RequestPart("data") Map<String, Object> data,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal User user) {

        Property p = buildPropertyFromMap(data);
        p.setPostedBy(user.getId());
        p.setStatus("pending");
        p.setExpiresAt(LocalDateTime.now().plusDays(30));

        // TODO: upload images to ImageKit and set URLs
        if (data.containsKey("imageUrls")) {
            @SuppressWarnings("unchecked")
            List<String> urls = (List<String>) data.get("imageUrls");
            p.setImage(urls);
        }

        propertyService.save(p);
        return ResponseEntity.ok(ApiResponse.ok("Listing submitted for review.", p));
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
        propertyService.save(p);
        return ResponseEntity.ok(ApiResponse.ok("Listing updated.", p));
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Property buildPropertyFromMap(Map<String, Object> d) {
        Property p = new Property();
        updatePropertyFromMap(p, d);
        return p;
    }

    @SuppressWarnings("unchecked")
    private void updatePropertyFromMap(Property p, Map<String, Object> d) {
        if (d.containsKey("title"))        p.setTitle((String) d.get("title"));
        if (d.containsKey("location"))     p.setLocation((String) d.get("location"));
        if (d.containsKey("price"))        p.setPrice(((Number) d.get("price")).doubleValue());
        if (d.containsKey("beds"))         p.setBeds(((Number) d.get("beds")).intValue());
        if (d.containsKey("baths"))        p.setBaths(((Number) d.get("baths")).intValue());
        if (d.containsKey("sqft"))         p.setSqft(((Number) d.get("sqft")).doubleValue());
        if (d.containsKey("type"))         p.setType((String) d.get("type"));
        if (d.containsKey("availability")) p.setAvailability((String) d.get("availability"));
        if (d.containsKey("description"))  p.setDescription((String) d.get("description"));
        if (d.containsKey("amenities"))    p.setAmenities((List<String>) d.get("amenities"));
        if (d.containsKey("phone"))        p.setPhone((String) d.get("phone"));
        if (d.containsKey("googleMapLink")) p.setGoogleMapLink((String) d.get("googleMapLink"));
    }
}
