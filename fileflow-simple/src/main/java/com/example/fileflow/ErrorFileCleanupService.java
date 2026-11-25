package com.example.fileflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ErrorFileCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ErrorFileCleanupService.class);
    private final SftpStorage storage;
    private final FileRepository repo;
    private final Clock clock;

    public ErrorFileCleanupService(SftpStorage storage, FileRepository repo, Clock clock) {
        this.storage = storage;
        this.repo = repo;
        this.clock = clock;
    }

    @Scheduled(cron = "${fileflow.error-cleanup.cron:0 0 2 * * *}", zone = "UTC")
    public void cleanup() {
        Instant cutoff = clock.instant().minus(7, ChronoUnit.DAYS);
        List<FileRecord> expired = repo.findByStatusAndErrorEnteredAtBefore(
                FileRecord.Status.FAILED, cutoff);
        for (FileRecord record : expired) {
            String expectedPath = "error/" + record.id + ".xml";
            if (!expectedPath.equals(record.storageKey)) {
                log.warn("Skipping unexpected error path for id={}", record.id);
                continue;
            }
            try {
                if (storage.exists(expectedPath)) {
                    storage.delete(expectedPath);
                }
                // Retain the DB record as an audit trail and to prevent re-uploading
                // the same content hash as though it were new.
                record.storageKey = null;
                record.updatedAt = clock.instant();
                repo.save(record);
                log.info("Expired error file cleaned: id={}", record.id);
            } catch (Exception ex) {
                log.warn("Cleanup pending for id={}: {}", record.id, ex.getMessage(), ex);
            }
        }
    }
}
