package com.docuchat.web;

import com.docuchat.config.CurrentUser;
import com.docuchat.service.DocumentIngestionService;
import com.docuchat.service.DocumentService;
import com.docuchat.web.dto.DeleteResponse;
import com.docuchat.web.dto.DocumentSummary;
import com.docuchat.web.dto.UploadResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentIngestionService ingestion;
    private final DocumentService documents;

    public DocumentController(DocumentIngestionService ingestion, DocumentService documents) {
        this.ingestion = ingestion;
        this.documents = documents;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestPart("file") MultipartFile file,
                                                 @RequestParam(defaultValue = "false") boolean forceReindex) {
        var result = ingestion.ingest(file, CurrentUser.id(), forceReindex);
        // 201 for a newly indexed document, 200 when deduplicated against an existing one
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return documents.list(CurrentUser.id());
    }

    @DeleteMapping("/{id}")
    public DeleteResponse delete(@PathVariable UUID id) {
        documents.delete(id, CurrentUser.id());
        return new DeleteResponse(id, true);
    }
}
