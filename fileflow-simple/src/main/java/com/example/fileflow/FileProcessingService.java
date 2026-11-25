
package com.example.fileflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class FileProcessingService {

    private static final Logger log =
            LoggerFactory.getLogger(FileProcessingService.class);

    private static final long MAX_SIZE = 50L * 1024 * 1024;

    private final LocalStorage storage;
    private final FileRepository repo;
    private final XmlValidator validator;
    private final PdfGenerator pdf;

    public FileProcessingService(
            LocalStorage storage,
            FileRepository repo,
            XmlValidator validator,
            PdfGenerator pdf) {

        this.storage = storage;
        this.repo = repo;
        this.validator = validator;
        this.pdf = pdf;
    }

    // ==================================================
    // 1. UPLOAD XML TO SFTP
    // ==================================================

    public synchronized FileRecord upload(MultipartFile file)
            throws Exception {

        if (file.isEmpty() || file.getSize() > MAX_SIZE) {
            throw new FileUploadException(
                    "XML must be between 1 byte and 50 MB");
        }

        String name = file.getOriginalFilename();

        if (name == null ||
                !name.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new FileUploadException("Only XML files allowed");
        }

        Path temp = Files.createTempFile("fileflow-", ".xml");

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            long count = 0;

            // Stream XML to local temporary file.
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(temp)) {

                byte[] buffer = new byte[65536];
                int n;

                while ((n = in.read(buffer)) != -1) {
                    count += n;

                    if (count > MAX_SIZE) {
                        throw new FileUploadException(
                                "XML exceeds 50 MB");
                    }

                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                }
            }

            if (count == 0) {
                throw new FileUploadException("Empty XML");
            }

            String id = HexFormat.of()
                    .formatHex(digest.digest());

            String incoming = incoming(id);
            String processed = processed(id);
            String pdfKey = pdfKey(id);

            log.info("Received XML: name={}, id={}", name, id);

            FileRecord record = repo.findById(id).orElse(null);

            // ------------------------------------------
            // Handle duplicate or existing records
            // ------------------------------------------

            if (record != null) {

                if (record.status == FileRecord.Status.COMPLETED) {

                    boolean xmlExists = storage.exists(processed);
                    boolean pdfExists = storage.exists(pdfKey);

                    if (xmlExists && pdfExists) {
                        log.info(
                                "Duplicate XML already completed: {}",
                                id);
                        return record;
                    }

                    log.warn(
                            "Completed record has missing files: " +
                                    "id={}, xml={}, pdf={}",
                            id, xmlExists, pdfExists);

                    if (xmlExists) {
                        // XML exists, regenerate missing PDF.
                        record.storageKey = processed;
                        record.pdfKey = null;
                        record.status = FileRecord.Status.PROCESSING;
                        record.updatedAt = Instant.now();
                        repo.save(record);

                        process(record);
                        return record;
                    }

                    // XML is missing: re-upload the XML.
                    resetForUpload(record);

                } else if (record.status == FileRecord.Status.FAILED) {

                    // Preserve failed-file retention information.
                    log.info("Previously failed XML: {}", id);
                    return record;

                } else {

                    // Try to recover an unfinished process.
                    if (storage.exists(incoming) ||
                            storage.exists(processed)) {

                        process(record);
                        return record;
                    }

                    // No committed XML is available.
                    // Re-upload the supplied file.
                    resetForUpload(record);
                }

            } else {
                record = repo.save(new FileRecord(id, name));
            }

            // ------------------------------------------
            // Upload XML using temporary .part filename
            // ------------------------------------------

            String part = incoming + ".part";

            log.info("Uploading XML to SFTP: {}", part);

            try (InputStream in = Files.newInputStream(temp)) {
                storage.write(part, in);
            }

            // Rename only after successful transfer.
            storage.move(part, incoming);

            if (!storage.exists(incoming)) {
                throw new IOException(
                        "SFTP upload verification failed: " + incoming);
            }

            record.storageKey = incoming;
            record.status = FileRecord.Status.RECEIVED;
            record.updatedAt = Instant.now();
            repo.save(record);

            log.info("XML committed to SFTP: {}", incoming);

            process(record);

            return record;

        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ==================================================
    // 2. VALIDATE XML AND GENERATE PDF
    // ==================================================

    public synchronized void process(FileRecord record)
            throws Exception {

        if (record.status == FileRecord.Status.FAILED) {
            return;
        }

        String id = record.id;

        String incoming = incoming(id);
        String processed = processed(id);
        String error = error(id);
        String pdfKey = pdfKey(id);

        log.info(
                "Processing XML: id={}, status={}",
                id, record.status);

        // ------------------------------------------
        // Reconcile physical SFTP files
        // ------------------------------------------

        if (storage.exists(processed)) {

            record.storageKey = processed;

            // A committed processed XML has already
            // passed validation in the normal workflow.
            if (record.status == FileRecord.Status.RECEIVED) {
                record.status = FileRecord.Status.VALIDATED;
            }

            repo.save(record);

        } else if (storage.exists(incoming)) {

            record.storageKey = incoming;
            record.status = FileRecord.Status.RECEIVED;
            repo.save(record);

        } else if (storage.exists(error)) {

            record.storageKey = error;
            record.status = FileRecord.Status.FAILED;

            if (record.errorEnteredAt == null) {
                record.errorEnteredAt = Instant.now();
            }

            record.updatedAt = Instant.now();
            repo.save(record);
            return;

        } else {

            throw new IOException(
                    "No committed XML found for id=" + id +
                            ". Check SFTP incoming/*.part");
        }

        // ------------------------------------------
        // Validate XML against XSD
        // ------------------------------------------

        if (record.status == FileRecord.Status.RECEIVED) {

            log.info("Validating XML: {}", id);

            try (InputStream in =
                         storage.read(record.storageKey)) {

                validator.validate(in);

            } catch (SAXException e) {

                log.warn("XML validation failed: {}", id, e);

                storage.move(record.storageKey, error);

                record.storageKey = error;
                record.status = FileRecord.Status.FAILED;
                record.errorEnteredAt = Instant.now();

                String message = e.getMessage();

                record.errorMessage = message == null
                        ? "Invalid XML"
                        : message.substring(
                        0, Math.min(1900, message.length()));

                record.updatedAt = Instant.now();
                repo.save(record);

                log.info("Invalid XML moved to error/: {}", id);
                return;
            }

            record.status = FileRecord.Status.VALIDATED;
            record.updatedAt = Instant.now();
            repo.save(record);

            log.info("XML validation successful: {}", id);
        }

        // ------------------------------------------
        // Move valid XML to processed/
        // ------------------------------------------

        if (!processed.equals(record.storageKey)) {

            storage.move(record.storageKey, processed);

            record.storageKey = processed;
            record.updatedAt = Instant.now();
            repo.save(record);

            log.info("XML moved to processed/: {}", processed);
        }

        // ------------------------------------------
        // Generate PDF using temporary file
        // ------------------------------------------

        record.status = FileRecord.Status.PROCESSING;
        record.updatedAt = Instant.now();
        repo.save(record);

        if (!storage.exists(pdfKey)) {

            Path tempPdf =
                    Files.createTempFile("fileflow-pdf-", ".pdf");

            try {
                log.info("Generating PDF: {}", pdfKey);

                try (InputStream in = storage.read(processed);
                     OutputStream out =
                             Files.newOutputStream(tempPdf)) {

                    pdf.generate(in, out);
                }

                String pdfPart = pdfKey + ".part";

                try (InputStream in =
                             Files.newInputStream(tempPdf)) {

                    storage.write(pdfPart, in);
                }

                storage.move(pdfPart, pdfKey);

                log.info("PDF uploaded to SFTP: {}", pdfKey);

            } finally {
                Files.deleteIfExists(tempPdf);
            }
        }

        // ------------------------------------------
        // Verify files before marking COMPLETED
        // ------------------------------------------

        if (!storage.exists(processed) ||
                !storage.exists(pdfKey)) {

            throw new IOException(
                    "XML or PDF missing on SFTP for id=" + id);
        }

        record.storageKey = processed;
        record.pdfKey = pdfKey;
        record.status = FileRecord.Status.COMPLETED;
        record.errorMessage = null;
        record.updatedAt = Instant.now();

        repo.save(record);

        log.info("Processing COMPLETED: {}", id);
    }

    // ==================================================
    // 3. RESET RECORD FOR RE-UPLOAD
    // ==================================================

    private void resetForUpload(FileRecord record) {

        log.warn(
                "Resetting record for re-upload: {}",
                record.id);

        record.status = FileRecord.Status.RECEIVED;
        record.storageKey = null;
        record.pdfKey = null;
        record.errorMessage = null;
        record.errorEnteredAt = null;
        record.updatedAt = Instant.now();

        repo.save(record);
    }

    // ==================================================
    // 4. GET FILE STATUS
    // ==================================================

    public FileRecord get(String id) {

        return repo.findById(id)
                .orElseThrow(() ->
                        new FileUploadException(
                                "Unknown file: " + id));
    }

    // ==================================================
    // 5. DOWNLOAD PDF
    // ==================================================

    public InputStream pdf(String id) throws IOException {

        FileRecord record = get(id);

        if (record.status != FileRecord.Status.COMPLETED ||
                record.pdfKey == null) {

            throw new FileUploadException("PDF not ready");
        }

        if (!storage.exists(record.pdfKey)) {
            throw new IOException(
                    "PDF missing from SFTP: " + record.pdfKey);
        }

        return storage.read(record.pdfKey);
    }

    // ==================================================
    // 6. RECOVERY AFTER RESTART
    // ==================================================

    public synchronized void recover() {

        log.info("Starting FileFlow recovery");

        for (FileRecord record :
                repo.findByStatusNot(FileRecord.Status.COMPLETED)) {

            if (record.status == FileRecord.Status.FAILED) {
                continue;
            }

            try {
                process(record);

            } catch (Exception e) {

                log.warn(
                        "Recovery pending for id={}: {}",
                        record.id, e.getMessage(), e);
            }
        }

        log.info("FileFlow recovery completed");
    }

    // ==================================================
    // 7. SFTP PATH HELPERS
    // ==================================================

    private String incoming(String id) {
        return "incoming/" + id + ".xml";
    }

    private String processed(String id) {
        return "processed/" + id + ".xml";
    }

    private String error(String id) {
        return "error/" + id + ".xml";
    }

    private String pdfKey(String id) {
        return "pdf/" + id + ".pdf";
    }
}
