package com.example.fileflow;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Entity
@Table(name = "file_records")
public class FileRecord {
    private static final Logger log = LoggerFactory.getLogger(FileRecord.class);
    @Id
    public String id;
    public String originalName;
    @Enumerated(EnumType.STRING)
    public Status status;
    public String storageKey;
    public String pdfKey;
    public Instant errorEnteredAt;
    @Column(length = 2000)
    public String errorMessage;
    public Instant updatedAt;

    protected FileRecord() {
        log.debug("JPA loading FileRecord instance");
    }

    public FileRecord(String id, String name) {
        this.id = id;
        this.originalName = name;
        this.status = Status.RECEIVED;
        this.updatedAt = Instant.now();
        log.debug("Created in-memory FileRecord id={} status={}", id, status);
    }

    public enum Status {RECEIVED, VALIDATED, PROCESSING, COMPLETED, FAILED}
}
