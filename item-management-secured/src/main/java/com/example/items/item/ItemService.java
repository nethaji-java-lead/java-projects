package com.example.items.item;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {
    private final ItemRepository repository;

    public ItemService(ItemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ItemResponse create(CreateItemRequest request, String ownerId) {
        // Ownership is taken from the verified token subject, never from client input.
        Item item = new Item(request.name(), request.description(), request.price(), ownerId);
        return ItemResponse.from(repository.save(item));
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> findAll() {
        return repository.findAll().stream().map(ItemResponse::from).toList();
    }
}
