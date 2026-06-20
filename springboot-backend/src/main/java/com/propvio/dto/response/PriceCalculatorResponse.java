package com.propvio.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PriceCalculatorResponse {

    // Core price fields (from ML model)
    private double predictedPriceLakhs;
    private double predictedPriceCrores;
    private long predictedPriceInr;

    // ±15% confidence range
    private double minPriceLakhs;
    private double maxPriceLakhs;
    private String minPriceFormatted;   // e.g. "₹85.2 L"
    private String maxPriceFormatted;   // e.g. "₹1.15 Cr"
    private String estimatedPrice;      // e.g. "₹1.0 Cr"

    // Per-sqft breakdown
    private long pricePerSqft;

    // Context
    private String city;
    private int bhk;
    private double areaSqft;
    private String furnishing;

    // Quick AI insight (generated server-side, no external API needed)
    private String insight;
    private String marketLabel;         // "Premium", "Mid-Range", "Affordable"
}
