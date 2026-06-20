package com.propvio.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PriceCalculatorRequest {

    @NotBlank(message = "City is required")
    private String city;

    @NotNull
    @Min(value = 1) @Max(value = 5)
    private Integer bhk;

    @NotNull
    @DecimalMin("300") @DecimalMax("5000")
    private Double areaSqft;

    @NotNull
    @DecimalMin("0") @DecimalMax("30")
    private Double ageYears;

    // Unfurnished | Semi-Furnished | Fully Furnished
    private String furnishing = "Semi-Furnished";
}
