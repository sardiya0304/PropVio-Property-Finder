package com.propvio.service;

import com.propvio.dto.request.PriceCalculatorRequest;
import com.propvio.dto.response.PriceCalculatorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiCalculatorService {

    private final ChatClient chatClient;

    record PriceEstimate(
        double estimatedPriceLakhs,
        double minPriceLakhs,
        double maxPriceLakhs,
        long pricePerSqft,
        String marketLabel,
        String insight
    ) {}

    public PriceCalculatorResponse calculate(PriceCalculatorRequest req) {
        try {
            String userPrompt = """
                Estimate the fair market price for a residential property in India with these details:
                - City: %s
                - BHK: %d
                - Area: %.0f sq ft
                - Property age: %.0f years
                - Furnishing: %s

                Use your knowledge of Indian real estate markets as of 2025-2026.
                Return ONLY a valid JSON object with these exact fields:
                {
                  "estimatedPriceLakhs": <number>,
                  "minPriceLakhs": <number>,
                  "maxPriceLakhs": <number>,
                  "pricePerSqft": <integer>,
                  "marketLabel": <"Premium"|"Mid-Range"|"Affordable">,
                  "insight": <string — 2-3 sentences>
                }
                """.formatted(req.getCity(), req.getBhk(), req.getAreaSqft(),
                              req.getAgeYears(), req.getFurnishing());

            PriceEstimate estimate = chatClient.prompt()
                .system("You are an expert Indian real estate valuation AI. Always respond with valid JSON only.")
                .user(userPrompt)
                .call()
                .entity(PriceEstimate.class);

            if (estimate == null) throw new RuntimeException("null response");

            return buildResponse(req, estimate.estimatedPriceLakhs(), estimate.minPriceLakhs(),
                estimate.maxPriceLakhs(), estimate.pricePerSqft(),
                estimate.marketLabel(), estimate.insight());

        } catch (Exception e) {
            // Fallback: formula-based estimate (no external API needed)
            return formulaFallback(req);
        }
    }

    // ── Formula-based fallback (works without OpenAI key) ───────────────────────

    private static final Map<String, Long> CITY_SQFT = Map.ofEntries(
        Map.entry("mumbai",       18000L),
        Map.entry("thane",        11000L),
        Map.entry("navi mumbai",  10500L),
        Map.entry("gurgaon",       9500L),
        Map.entry("delhi",         9000L),
        Map.entry("delhi ncr",     8500L),
        Map.entry("noida",         7500L),
        Map.entry("bangalore",     7500L),
        Map.entry("pune",          7000L),
        Map.entry("chennai",       6500L),
        Map.entry("hyderabad",     6000L),
        Map.entry("kolkata",       5500L),
        Map.entry("ahmedabad",     5000L),
        Map.entry("jaipur",        5000L),
        Map.entry("lucknow",       4500L),
        Map.entry("indore",        4500L),
        Map.entry("nagpur",        4500L),
        Map.entry("chandigarh",    5500L),
        Map.entry("kochi",         5500L),
        Map.entry("surat",         4800L),
        Map.entry("vadodara",      4500L),
        Map.entry("nashik",        4500L),
        Map.entry("mysore",        5000L)
    );

    private PriceCalculatorResponse formulaFallback(PriceCalculatorRequest req) {
        long ppsf = CITY_SQFT.getOrDefault(req.getCity().toLowerCase(), 5000L);

        double bhkFactor     = 1.0 + (req.getBhk() - 2) * 0.05;
        double ageFactor     = Math.max(0.65, 1.0 - req.getAgeYears() * 0.012);
        double furnishFactor = "Fully Furnished".equalsIgnoreCase(req.getFurnishing()) ? 1.12
                             : "Semi-Furnished".equalsIgnoreCase(req.getFurnishing())  ? 1.06 : 1.0;

        double inr    = ppsf * req.getAreaSqft() * bhkFactor * ageFactor * furnishFactor;
        double lakhs  = inr / 100_000.0;
        double minL   = round2(lakhs * 0.88);
        double maxL   = round2(lakhs * 1.12);

        String label  = ppsf >= 12000 ? "Premium" : ppsf >= 6500 ? "Mid-Range" : "Affordable";

        String furnishNote = "Fully Furnished".equalsIgnoreCase(req.getFurnishing()) ? "12% furnishing premium"
                           : "Semi-Furnished".equalsIgnoreCase(req.getFurnishing())  ? "6% furnishing premium"
                           : "no furnishing premium";

        String insight = String.format(
            "%s falls in the %s segment at ₹%,d/sqft. "
            + "A %dBHK of %.0f sqft with %s is estimated at %s. "
            + "Property age of %.0f years discounts the base value by %.0f%% (%s).",
            req.getCity(), label.toLowerCase(), ppsf, req.getBhk(), req.getAreaSqft(),
            req.getFurnishing().toLowerCase(), formatLakhs(lakhs),
            req.getAgeYears(), Math.max(0, (1.0 - ageFactor) * 100), furnishNote
        );

        return buildResponse(req, lakhs, minL, maxL, ppsf, label, insight);
    }

    // ── Shared builder ────────────────────────────────────────────────────────

    private PriceCalculatorResponse buildResponse(PriceCalculatorRequest req,
            double estimatedLakhs, double minLakhs, double maxLakhs,
            long pricePerSqft, String marketLabel, String insight) {
        return PriceCalculatorResponse.builder()
            .predictedPriceLakhs(round2(estimatedLakhs))
            .predictedPriceCrores(round2(estimatedLakhs / 100.0))
            .predictedPriceInr((long)(estimatedLakhs * 100_000))
            .minPriceLakhs(round2(minLakhs))
            .maxPriceLakhs(round2(maxLakhs))
            .minPriceFormatted(formatLakhs(minLakhs))
            .maxPriceFormatted(formatLakhs(maxLakhs))
            .estimatedPrice(formatLakhs(estimatedLakhs))
            .pricePerSqft(pricePerSqft)
            .city(req.getCity())
            .bhk(req.getBhk())
            .areaSqft(req.getAreaSqft())
            .furnishing(req.getFurnishing())
            .insight(insight)
            .marketLabel(marketLabel)
            .build();
    }

    private String formatLakhs(double lakhs) {
        if (lakhs >= 100) return String.format("₹%.2f Cr", lakhs / 100.0);
        return String.format("₹%.1f L", lakhs);
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
