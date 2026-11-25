package com.example.fileflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class LocalStorage implements Storage {
    private static final Logger log = LoggerFactory.getLogger(LocalStorage.class);
    private final Path root;

    public LocalStorage(@Value("${fileflow.storage.local-root:./local-storage}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        log.info("Initialized local storage at {}", this.root);
    }

    private Path path(String key) throws IOException {
        Path result = root.resolve(key).normalize();
        if (!result.startsWith(root)) {
            IOException e = new IOException("Invalid path");
            log.error("Rejected invalid local storage key {}", key);
            throw e;
        }
        return result;
    }

    public void write(String key, InputStream in) throws IOException {
        log.debug("Writing local storage key {}", key);
        Path p = path(key);
        Files.createDirectories(p.getParent());
        try (OutputStream out = Files.newOutputStream(p)) {
            in.transferTo(out);
        }
    }

    public InputStream read(String key) throws IOException {
        return Files.newInputStream(path(key));
    }

    public void move(String from, String to) throws IOException {
        log.debug("Moving local storage key {} to {}", from, to);
        Path target = path(to);
        Files.createDirectories(target.getParent());
        try {
            Files.move(path(from), target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(path(from), target);
        }
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }

    public boolean exists(String key) throws IOException {
        return Files.exists(path(key));
    }

    public List<String> list(String dir) throws IOException {
        Path d = path(dir);
        if (!Files.isDirectory(d)) return List.of();
        try (var s = Files.list(d)) {
            return s.filter(Files::isRegularFile).map(x -> dir + "/" + x.getFileName()).toList();
        }
    }
}
