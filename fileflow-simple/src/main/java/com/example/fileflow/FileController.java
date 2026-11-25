package com.example.fileflow;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.InputStreamResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final FileProcessingService service;

    public FileController(FileProcessingService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileRecord upload(@RequestParam("file") MultipartFile file) throws Exception {
        log.info("Received upload request: {}", file.getOriginalFilename());
        try {
            FileRecord record = service.upload(file);
            log.info("Upload completed for file={} id={}", file.getOriginalFilename(), record.id);
            return record;
        } catch (Exception e) {
            log.error("Upload failed for file={}: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public FileRecord get(@PathVariable String id) {
        log.debug("Retrieving file record {}", id);
        return service.get(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> pdf(@PathVariable String id) throws Exception {
        log.info("Generating PDF download response for id={}", id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+id+".pdf").body(new InputStreamResource(service.pdf(id)));
    }
}
