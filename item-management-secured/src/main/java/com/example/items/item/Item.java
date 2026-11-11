package com.example.items.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "items")
public class Item {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "owner_id", nullable = false, length = 100)
    private String ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Item() {}

    public Item(String name, String description, BigDecimal price, String ownerId) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.ownerId = ownerId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getOwnerId() { return ownerId; }
    public Instant getCreatedAt() { return createdAt; }
}
