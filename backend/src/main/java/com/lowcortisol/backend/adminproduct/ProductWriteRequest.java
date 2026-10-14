package com.lowcortisol.backend.adminproduct;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ProductWriteRequest(
    @NotNull @Positive Long categoryId,
    @NotBlank @Size(max = 255) String productName,
    @Size(max = 20000) String productDescription,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal productPrice,
    @Size(max = 10) List<@NotBlank @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String> productImages,
    JsonNode productAttributes,
    @Min(0) Integer productQuantity,
    Boolean isVisible,
    @Min(0) Long expectedVersion
) {}
