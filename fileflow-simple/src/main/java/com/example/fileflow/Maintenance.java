package com.example.fileflow;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;

@Component
public class Maintenance {
    private static final Logger log = LoggerFactory.getLogger(Maintenance.class);
    private final FileProcessingService service;
    private final FileRepository repo;
    private final Storage storage;
    @Value("${fileflow.error-retention-days:7}")
    int days;

    public Maintenance(FileProcessingService service, FileRepository repo, Storage storage) {
        this.service = service;
        this.repo = repo;
        this.storage = storage;
    }

    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void startup() {
        log.info("Application ready; starting startup recovery");
        service.recover();
    }

    @Scheduled(fixedDelay = 60000)
    public void retry() {
        log.debug("Running scheduled retry");
        service.recover();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanup() {
        log.info("Starting failed-file cleanup with retention={} days", days);
        int cleaned = 0;
        for (FileRecord r : repo.findByStatusAndErrorEnteredAtBefore(FileRecord.Status.FAILED, Instant.now().minus(days, java.time.temporal.ChronoUnit.DAYS))) {
            try {
                if (r.storageKey != null) storage.delete(r.storageKey);
                r.storageKey = null;
                repo.save(r);
                cleaned++;
            } catch (Exception e) {
                log.error("Failed to clean expired file id={}: {}", r.id, e.getMessage(), e);
            }
        }
        log.info("Cleanup completed; cleaned={} records", cleaned);
    }
}
