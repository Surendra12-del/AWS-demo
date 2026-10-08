package com.example.aws.demo.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for creating/updating a {@link Product}.
 */
public record ProductRequest(
        @NotBlank(message = "name is required") String name,
        String description,
        @PositiveOrZero(message = "price must be zero or positive") double price) {
}
