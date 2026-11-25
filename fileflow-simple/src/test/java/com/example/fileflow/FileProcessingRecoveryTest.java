
package com.example.fileflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileProcessingRecoveryTest {

    private static final String ID = "test-file-id";
    private static final String INCOMING = "incoming/" + ID + ".xml";
    private static final String PROCESSED = "processed/" + ID + ".xml";
    private static final String PDF_PART = "pdf/" + ID + ".pdf.part";
    private static final String PDF = "pdf/" + ID + ".pdf";

    @Mock
    private LocalStorage storage;

    @Mock
    private FileRepository repo;

    @Mock
    private XmlValidator validator;

    @Mock
    private PdfGenerator pdf;

    private final Map<String, byte[]> remoteFiles = new HashMap<>();

    private FileRecord record;
    private FileProcessingService service;

    @BeforeEach
    void setUp() throws Exception {

        // Simulate an XML file committed to SFTP
        remoteFiles.clear();
        remoteFiles.put(
                INCOMING,
                "<document><title>Title</title>"
                        .concat("<content>Content</content></document>")
                        .getBytes(StandardCharsets.UTF_8)
        );

        // Mock SFTP file existence
        when(storage.exists(anyString()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(0);
                    return remoteFiles.containsKey(path);
                });

        // Mock SFTP file reading
        when(storage.read(anyString()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(0);
                    byte[] content = remoteFiles.get(path);

                    if (content == null) {
                        throw new IllegalStateException(
                                "SFTP file not found: " + path);
                    }

                    return new ByteArrayInputStream(content);
                });

        // Mock SFTP file writing
        // Supports ChannelInputStream and ByteArrayInputStream
        doAnswer(invocation -> {
            String path = invocation.getArgument(0);
            InputStream input = invocation.getArgument(1);

            remoteFiles.put(path, input.readAllBytes());
            return null;
        }).when(storage).write(
                anyString(),
                any(InputStream.class)
        );

        // Mock SFTP atomic file movement
        doAnswer(invocation -> {
            String source = invocation.getArgument(0);
            String target = invocation.getArgument(1);

            byte[] content = remoteFiles.remove(source);

            if (content == null) {
                throw new IllegalStateException(
                        "Source file not found: " + source);
            }

            remoteFiles.put(target, content);
            return null;
        }).when(storage).move(
                anyString(),
                anyString()
        );

        // Mock PDF generation
        // Supports ChannelOutputStream and ByteArrayOutputStream
        doAnswer(invocation -> {
            OutputStream output = invocation.getArgument(1);

            output.write(
                    "%PDF-1.4\nTest PDF content"
                            .getBytes(StandardCharsets.UTF_8)
            );

            output.flush();
            return null;
        }).when(pdf).generate(
                any(),
                any(OutputStream.class)
        );

        // Simulate database record before application restart
        record = new FileRecord(ID, "sample.xml");
        record.updatedAt = Instant.now();

        // Return unfinished records during recovery
        when(repo.findByStatusNot(FileRecord.Status.COMPLETED))
                .thenReturn(List.of(record));

        // Mock database save
        when(repo.save(any(FileRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Initialize service
        service = new FileProcessingService(
                storage,
                repo,
                validator,
                pdf
        );
    }

    @Test
    void recoversCommittedIncomingXmlAfterRestartAndPublishesPdf()
            throws Exception {

        // Execute recovery
        service.recover();

        // Verify database status
        assertEquals(
                FileRecord.Status.COMPLETED,
                record.status,
                "Recovered file should be COMPLETED"
        );

        // Verify final SFTP paths
        assertEquals(PROCESSED, record.storageKey);
        assertEquals(PDF, record.pdfKey);

        // Verify processed XML exists
        assertTrue(
                remoteFiles.containsKey(PROCESSED),
                "Processed XML should exist"
        );

        // Verify PDF exists
        assertTrue(
                remoteFiles.containsKey(PDF),
                "Generated PDF should exist"
        );

        // Verify PDF is not empty
        assertTrue(
                remoteFiles.get(PDF).length > 0,
                "Generated PDF should not be empty"
        );

        // Verify original incoming XML was moved
        assertFalse(
                remoteFiles.containsKey(INCOMING),
                "Incoming XML should be removed after processing"
        );

        // Verify temporary PDF was published
        assertFalse(
                remoteFiles.containsKey(PDF_PART),
                "Temporary PDF should not remain"
        );

        // Verify timestamp updated
        assertNotNull(record.updatedAt);

        // Verify XML validation
        verify(validator).validate(any());

        // Verify XML moved to processed directory
        verify(storage).move(INCOMING, PROCESSED);

        // Verify temporary PDF moved to final directory
        verify(storage).move(PDF_PART, PDF);

        // Verify database persisted
        verify(repo, atLeastOnce()).save(record);
    }
}
