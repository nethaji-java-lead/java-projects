package com.example.items.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
    UUID id,
    String name,
    String description,
    BigDecimal price,
    String ownerId,
    Instant createdAt
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getDescription(),
            item.getPrice(), item.getOwnerId(), item.getCreatedAt());
    }
}
