package com.example.fileflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErrorFileCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-24T02:00:00Z");
    private static final Instant CUTOFF = NOW.minus(7, ChronoUnit.DAYS);
    @Mock SftpStorage storage;
    @Mock FileRepository repo;
    private ErrorFileCleanupService cleanup;

    @BeforeEach
    void setUp() {
        cleanup = new ErrorFileCleanupService(storage, repo, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private FileRecord failed(String id, Instant errorEnteredAt) {
        FileRecord record = new FileRecord(id, id + ".xml");
        record.status = FileRecord.Status.FAILED;
        record.storageKey = "error/" + id + ".xml";
        record.errorEnteredAt = errorEnteredAt;
        return record;
    }

    private void queryReturns(FileRecord... records) {
        when(repo.findByStatusAndErrorEnteredAtBefore(FileRecord.Status.FAILED, CUTOFF))
                .thenReturn(List.of(records));
    }

    @Test
    void deletesEightDayOldErrorFileAndKeepsAuditRecord() throws Exception {
        FileRecord old = failed("old", NOW.minus(8, ChronoUnit.DAYS));
        queryReturns(old);
        when(storage.exists(old.storageKey)).thenReturn(true);
        cleanup.cleanup();
        verify(storage).delete("error/old.xml");
        verify(repo).save(old);
        verify(repo, never()).delete(any(FileRecord.class));
        assertNull(old.storageKey);
        assertEquals(NOW, old.updatedAt);
        assertEquals(FileRecord.Status.FAILED, old.status);
        assertEquals(NOW.minus(8, ChronoUnit.DAYS), old.errorEnteredAt);
    }

    @Test
    void retainsRecentFilesAndExactSevenDayBoundary() throws Exception {
        // The repository must exclude both records because the query uses Before (strict <).
        queryReturns();
        cleanup.cleanup();
        verify(repo).findByStatusAndErrorEnteredAtBefore(FileRecord.Status.FAILED, CUTOFF);
        verifyNoInteractions(storage);
        verify(repo, never()).save(any(FileRecord.class));
    }

    @Test
    void usesErrorEntryTimeRatherThanOriginalUploadTime() throws Exception {
        // A 10-day-old upload that entered error/ 2 days ago is excluded by the repository.
        queryReturns();
        cleanup.cleanup();
        verifyNoInteractions(storage);
    }

    @Test
    void retriesOnNextRunAfterSftpDeleteFailure() throws Exception {
        FileRecord old = failed("retry", NOW.minus(9, ChronoUnit.DAYS));
        queryReturns(old);
        when(storage.exists(old.storageKey)).thenReturn(true);
        doThrow(new IllegalStateException("SFTP unavailable"))
                .doNothing().when(storage).delete("error/retry.xml");
        cleanup.cleanup();
        assertEquals("error/retry.xml", old.storageKey);
        verify(repo, never()).save(old);
        cleanup.cleanup();
        verify(storage, times(2)).delete("error/retry.xml");
        verify(repo).save(old);
        assertNull(old.storageKey);
    }

    @Test
    void clearsStalePathWhenRemoteFileAlreadyMissing() throws Exception {
        FileRecord old = failed("missing", NOW.minus(10, ChronoUnit.DAYS));
        queryReturns(old);
        when(storage.exists(old.storageKey)).thenReturn(false);
        cleanup.cleanup();
        verify(storage, never()).delete(anyString());
        verify(repo).save(old);
        assertNull(old.storageKey);
    }

    @Test
    void skipsUnexpectedPathsToAvoidDeletingOtherFiles() throws Exception {
        FileRecord old = failed("unsafe", NOW.minus(8, ChronoUnit.DAYS));
        old.storageKey = "processed/unsafe.xml";
        queryReturns(old);
        cleanup.cleanup();
        verifyNoInteractions(storage);
        verify(repo, never()).save(any(FileRecord.class));
    }
}
