package com.example.fileflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
public interface FileRepository extends JpaRepository<FileRecord,String> {
 List<FileRecord> findByStatusNot(FileRecord.Status status);
 List<FileRecord> findByStatusAndErrorEnteredAtBefore(FileRecord.Status status,Instant before);

}
