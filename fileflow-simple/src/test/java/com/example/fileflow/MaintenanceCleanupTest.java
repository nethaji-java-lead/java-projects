package com.example.fileflow;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceCleanupTest {
    @Test
    void cleanupDeletesErrorFileOlderThanSevenDaysUsingErrorEnteredAt() throws Exception {
        FileProcessingService service = mock(FileProcessingService.class);
        FileRepository repo = mock(FileRepository.class);
        Storage storage = mock(Storage.class);
        Maintenance maintenance = new Maintenance(service, repo, storage);
        ReflectionTestUtils.setField(maintenance, "days", 7);

        FileRecord expired = new FileRecord("expired-id", "invalid.xml");
        expired.status = FileRecord.Status.FAILED;
        expired.storageKey = "error/expired-id.xml";
        expired.errorEnteredAt = Instant.now().minus(Duration.ofDays(8));
        when(repo.findByStatusAndErrorEnteredAtBefore(
                org.mockito.ArgumentMatchers.eq(FileRecord.Status.FAILED),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(expired));

        Instant beforeCleanup = Instant.now();
        maintenance.cleanup();
        Instant afterCleanup = Instant.now();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repo).findByStatusAndErrorEnteredAtBefore(
                org.mockito.ArgumentMatchers.eq(FileRecord.Status.FAILED),
                cutoff.capture());
        Instant expectedCutoff = Instant.now().minus(Duration.ofDays(7));
        assertTrue(Duration.between(cutoff.getValue(), expectedCutoff)
                .abs().compareTo(Duration.ofSeconds(1)) < 0);
        assertTrue(cutoff.getValue().isAfter(beforeCleanup.minusSeconds(1)));
        assertTrue(cutoff.getValue().isBefore(afterCleanup.plusSeconds(1)));

        verify(storage).delete("error/expired-id.xml");
        verify(repo).save(expired);
        assertEquals(FileRecord.Status.FAILED, expired.status);
        assertNull(expired.storageKey);
        assertEquals(Instant.now().minus(Duration.ofDays(8)).getEpochSecond(),
                expired.errorEnteredAt.getEpochSecond());
    }
}
