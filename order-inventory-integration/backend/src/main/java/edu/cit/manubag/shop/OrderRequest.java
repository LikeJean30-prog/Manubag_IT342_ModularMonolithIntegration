package edu.cit.manubag.shop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrderRequest(
        @NotEmpty(message = "items must not be empty") @Valid List<OrderItemRequest> items
) {}
