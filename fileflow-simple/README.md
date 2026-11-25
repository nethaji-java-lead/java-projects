# FileFlow — simple Spring Boot XML → PDF

Java 17 / Spring Boot 3.5 / PostgreSQL / SSHJ / PDFBox.

The application connects to PostgreSQL at `localhost:5433`, database `fileflow`,
by default. Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`
to override the connection settings. The existing `FileRecord` JPA entity is
used to create/update the `file_records` table (`spring.jpa.hibernate.ddl-auto=update`).

## Run with local storage

```bash
mvn spring-boot:run
curl -F 'file=@sample.xml' http://localhost:8080/api/files/upload
```

The response contains a SHA-256 `id`. Download with:

```bash
curl -o output.pdf http://localhost:8080/api/files/REPLACE_ID/pdf
```

## Run with SFTP

```bash
docker compose up -d
ssh-keyscan -p 2222 localhost > known_hosts
```

Verify the host key fingerprint through a trusted channel before accepting it. Change `fileflow.storage.type=sftp` in `application.properties`, then run the app. The SFTP root `/upload` is relative to the demo user's chroot (`/home/demo/upload` on the server). Do not use demo credentials in production.

## Paths

`incoming/<sha>.xml.part` during transfer; `incoming/<sha>.xml` after rename; `processed/<sha>.xml` for valid XML; `error/<sha>.xml` for invalid XML; `pdf/<sha>.pdf.part` then `pdf/<sha>.pdf`.

## Scenarios

1. Upload `sample.xml`: expect COMPLETED and one PDF.
2. Upload `invalid.xml`: expect validation error, FAILED database record, and error XML.
3. Upload the same valid file again: same ID and no second PDF.
4. Stop the app after incoming file is renamed or while PDF is being generated; restart to retry incomplete records.

## Important limitations

This is a **simple demo**, not a production-grade exactly-once engine. Processing is synchronized inside one JVM; multiple instances require database claiming/locking. Interrupted `.part` uploads before the first successful rename are left for manual cleanup/re-upload. If a network transfer fails before the database storage key is recorded, the record may require operator intervention. SFTP rename atomicity depends on the server/filesystem and SSHJ's rename semantics. The PDF renderer demonstrates basic text extraction and outputs only the first 48 lines of ASCII-normalized content, not an arbitrary XML-to-layout template. Failed non-validation operations are retried every minute, without retry limits. Use encrypted credentials/keys and secure secret management in production.
