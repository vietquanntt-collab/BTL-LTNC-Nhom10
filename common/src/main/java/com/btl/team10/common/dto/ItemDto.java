package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.ItemType;

import java.time.Instant;

public record ItemDto(
        long itemId,
        long sellerId,
        String name,
        String description,
        ItemType itemType,
        String imageBase64,
        Instant createdAt,
        Instant updatedAt) {

    public ItemDto {
        requirePositive(itemId, "itemId");
        requirePositive(sellerId, "sellerId");
        if (name == null || name.isBlank() || name.trim().length() > 200) {
            throw new ValidationException("name", "must contain 1 to 200 non-blank characters");
        }
        if (description == null) {
            throw new ValidationException("description", "must not be null");
        }
        if (itemType == null) {
            throw new ValidationException("itemType", "must not be null");
        }
        if (createdAt == null || updatedAt == null) {
            throw new ValidationException("timestamps", "must not be null");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
