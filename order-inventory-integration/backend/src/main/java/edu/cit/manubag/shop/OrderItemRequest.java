package edu.cit.manubag.shop;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderItemRequest(
        @NotBlank(message = "productId is required") String productId,
        @Min(value = 1, message = "quantity must be at least 1") int quantity
) {}