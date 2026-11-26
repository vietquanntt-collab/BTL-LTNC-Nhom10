package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.ItemType;

public record CreateItemCommand(
        long sellerId,
        String name,
        String description,
        ItemType itemType,
        String imageBase64) {

    public CreateItemCommand {
        if (sellerId <= 0) {
            throw new ValidationException("sellerId", "must be positive");
        }
        if (name == null || name.isBlank() || name.trim().length() > 200) {
            throw new ValidationException("name", "must contain 1 to 200 non-blank characters");
        }
        if (description == null) {
            description = "";
        } else {
            description = description.trim();
        }
        if (itemType == null) {
            throw new ValidationException("itemType", "must not be null");
        }
    }
}
