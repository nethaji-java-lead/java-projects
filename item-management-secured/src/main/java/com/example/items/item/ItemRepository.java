package com.example.items.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findAllByOwnerId(String ownerId);
    Optional<Item> findByIdAndOwnerId(UUID id, String ownerId);
}
