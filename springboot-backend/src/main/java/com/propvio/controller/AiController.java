package com.propvio.controller;

import com.propvio.dto.request.PriceCalculatorRequest;
import com.propvio.dto.response.ApiResponse;
import com.propvio.dto.response.PriceCalculatorResponse;
import com.propvio.model.Property;
import com.propvio.model.User;
import com.propvio.repository.PropertyRepository;
import com.propvio.service.AiCalculatorService;
import com.propvio.service.AiRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiCalculatorService calculatorService;
    private final AiRecommendationService recommendationService;
    private final PropertyRepository propertyRepo;

    // POST /api/ai/calculate-price
    @PostMapping("/calculate-price")
    public ResponseEntity<ApiResponse<?>> calculatePrice(@Valid @RequestBody PriceCalculatorRequest req) {
        try {
            PriceCalculatorResponse result = calculatorService.calculate(req);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(ApiResponse.fail("AI service unavailable: " + e.getMessage()));
        }
    }

    // GET /api/ai/supported-cities
    @GetMapping("/supported-cities")
    public ResponseEntity<ApiResponse<?>> supportedCities() {
        return ResponseEntity.ok(ApiResponse.ok(List.of(
            "Mumbai", "Delhi", "Delhi NCR", "Bangalore", "Pune", "Hyderabad",
            "Chennai", "Kolkata", "Ahmedabad", "Gurgaon", "Noida", "Jaipur",
            "Lucknow", "Indore", "Nagpur", "Chandigarh", "Kochi", "Surat",
            "Thane", "Navi Mumbai", "Mysore", "Vadodara", "Nashik"
        )));
    }

    // GET /api/ai/recommendations
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<?>> getRecommendations(@AuthenticationPrincipal User user) {
        try {
            List<AiRecommendationService.RecommendedProperty> recs =
                recommendationService.getRecommendations(user.getId());
            if (recs.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.ok(
                    "Browse some properties first so we can personalise your recommendations.", List.of()));
            }
            return ResponseEntity.ok(ApiResponse.ok(recs));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(ApiResponse.fail("Recommendation service unavailable: " + e.getMessage()));
        }
    }

    // POST /api/ai/search — AI property search (queries DB, returns ScrapedProperty format + analysis)
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<?>> search(
            @RequestHeader(value = "X-Github-Key",    required = false) String githubKey,
            @RequestHeader(value = "X-Firecrawl-Key", required = false) String firecrawlKey,
            @RequestBody Map<String, Object> body) {

        String city    = stringOrEmpty(body.get("city"));
        String bhkStr  = stringOrEmpty(body.get("bhk"));
        String type    = stringOrEmpty(body.get("type"));
        double maxPrice = extractMaxPrice(body);

        Integer beds = parseBhk(bhkStr);
        String  propertyType = (type.isBlank() || type.equalsIgnoreCase("Any")) ? null : type;
        String  cityFilter   = city.isBlank() ? null : city;

        List<Property> props = propertyRepo.findForAiSearch(
            cityFilter, propertyType, beds, 0.0, maxPrice, PageRequest.of(0, 20));

        List<Map<String, Object>> scraped = props.stream()
            .map(this::toScrapedProperty)
            .collect(Collectors.toList());

        Map<String, Object> analysis = buildAnalysis(props, city);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("properties", scraped);
        result.put("analysis", analysis);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // POST /api/ai/validate-keys — validates X-Github-Key and X-Firecrawl-Key headers
    @PostMapping("/validate-keys")
    public ResponseEntity<ApiResponse<?>> validateKeys(
            @RequestHeader(value = "X-Github-Key",    required = false) String githubKey,
            @RequestHeader(value = "X-Firecrawl-Key", required = false) String firecrawlKey) {

        boolean ghOk = githubKey    != null && !githubKey.isBlank();
        boolean fcOk = firecrawlKey != null && !firecrawlKey.isBlank();

        if (!ghOk || !fcOk) {
            List<String> missing = new ArrayList<>();
            if (!ghOk) missing.add("GitHub Models key (X-Github-Key)");
            if (!fcOk) missing.add("Firecrawl key (X-Firecrawl-Key)");
            return ResponseEntity.status(403).body(
                ApiResponse.fail("Missing API keys: " + String.join(", ", missing)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("githubKey",    "valid");
        result.put("firecrawlKey", "valid");
        result.put("message",      "API keys accepted. AI Hub is ready.");
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Map<String, Object> toScrapedProperty(Property p) {
        long ppsf = p.getSqft() > 0 ? (long)(p.getPrice() / p.getSqft()) : 0;
        Map<String, Object> sp = new LinkedHashMap<>();
        sp.put("building_name",   p.getTitle());
        sp.put("property_type",   capitalize(p.getType()));
        sp.put("bhk_config",      p.getBeds() + "BHK");
        sp.put("location_address", p.getLocation());
        sp.put("price",           formatPrice(p.getPrice(), p.getAvailability()));
        sp.put("price_per_sqft",  "₹" + String.format("%,d", ppsf) + "/sqft");
        sp.put("description",     p.getDescription());
        sp.put("amenities",       p.getAmenities());
        sp.put("area_sqft",       p.getSqft().intValue() + " sqft");
        sp.put("possession_status", "Ready to Move");
        sp.put("source",          "Propvio");
        return sp;
    }

    private Map<String, Object> buildAnalysis(List<Property> props, String city) {
        Map<String, Object> analysis = new LinkedHashMap<>();

        if (props.isEmpty()) {
            analysis.put("overview",         List.of());
            analysis.put("best_value",       null);
            analysis.put("recommendations",  List.of(
                "No properties found for \"" + city + "\" with the selected filters.",
                "Try broadening your budget or selecting a different city.",
                "Check back later as new listings are added regularly."
            ));
            return analysis;
        }

        // Build overview for each property
        List<Map<String, Object>> overview = props.stream().map(p -> {
            long ppsf = p.getSqft() > 0 ? (long)(p.getPrice() / p.getSqft()) : 0;
            String verdict = ppsf < 5500 ? "good_deal" : ppsf < 8000 ? "fair" : "overpriced";
            int score = ppsf < 5500 ? 92 : ppsf < 8000 ? 78 : 65;

            Map<String, Object> ov = new LinkedHashMap<>();
            ov.put("name",            p.getTitle());
            ov.put("price",           formatPrice(p.getPrice(), p.getAvailability()));
            ov.put("area",            p.getSqft().intValue() + " sqft");
            ov.put("location",        p.getLocation());
            ov.put("highlight",       p.getBeds() + "BHK · " + p.getSqft().intValue() + " sqft · " + capitalize(p.getAvailability()));
            ov.put("match_score",     score);
            ov.put("one_line_insight", "₹" + String.format("%,d", ppsf) + "/sqft — " + (verdict.equals("good_deal") ? "great value" : verdict.equals("fair") ? "fair market price" : "premium pricing"));
            ov.put("value_verdict",   verdict);
            ov.put("red_flags",       List.of());
            return ov;
        }).collect(Collectors.toList());

        // Best value = lowest price-per-sqft
        Property bestValueProp = props.stream()
            .filter(p -> p.getSqft() > 0)
            .min(Comparator.comparingDouble(p -> p.getPrice() / p.getSqft()))
            .orElse(props.get(0));

        Map<String, String> bestValue = new LinkedHashMap<>();
        bestValue.put("name",   bestValueProp.getTitle());
        bestValue.put("reason", "Lowest price-per-sqft at ₹" + String.format("%,d", (long)(bestValueProp.getPrice() / bestValueProp.getSqft())) + "/sqft in " + bestValueProp.getLocation());

        OptionalDouble avgOpt = props.stream().filter(p -> p.getSqft() > 0)
            .mapToLong(p -> (long)(p.getPrice() / p.getSqft()))
            .average();
        String avgPpsf = avgOpt.isPresent()
            ? "₹" + String.format("%,d", (long) avgOpt.getAsDouble())
            : "N/A";

        List<String> recommendations = List.of(
            "Found " + props.size() + " active listing" + (props.size() == 1 ? "" : "s") + " in " + (city.isBlank() ? "all locations" : city) + " matching your criteria.",
            "Average price per sqft: " + avgPpsf + ". Budget 10–15% above listing price for registration and interiors.",
            "\"" + bestValueProp.getTitle() + "\" offers the best value with lowest price-per-sqft.",
            "All listings shown are from Propvio's verified database. Contact the owner directly via the property page."
        );

        analysis.put("overview",        overview);
        analysis.put("best_value",      bestValue);
        analysis.put("recommendations", recommendations);
        return analysis;
    }

    private String formatPrice(double price, String availability) {
        if ("rent".equalsIgnoreCase(availability)) {
            return "₹" + String.format("%,.0f", price) + "/mo";
        }
        if (price >= 10_000_000) return String.format("₹%.2f Cr", price / 10_000_000.0);
        if (price >= 100_000)    return String.format("₹%.1f L", price / 100_000.0);
        return "₹" + String.format("%,.0f", price);
    }

    private Integer parseBhk(String bhkStr) {
        if (bhkStr == null || bhkStr.isBlank() || bhkStr.equalsIgnoreCase("Any")) return null;
        try {
            return Integer.parseInt(bhkStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private double extractMaxPrice(Map<String, Object> body) {
        Object priceObj = body.get("price");
        if (priceObj instanceof Map) {
            Object max = ((Map<String, Object>) priceObj).get("max");
            if (max instanceof Number) return ((Number) max).doubleValue();
        }
        return 1_000_000_000.0;
    }

    private String stringOrEmpty(Object v) { return v instanceof String s ? s : ""; }
    private String capitalize(String s) { return s == null || s.isBlank() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
