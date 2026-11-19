package com.docuchat.service;

import com.docuchat.config.DocuChatProperties;
import com.docuchat.domain.DocumentRegistry;
import com.docuchat.domain.DocumentRegistryRepository;
import com.docuchat.domain.DocumentStatus;
import com.docuchat.exception.ApiException;
import com.docuchat.exception.ErrorCode;
import com.docuchat.web.dto.UploadResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** ETL write path: Reader -> Sanitizer -> TokenTextSplitter -> metadata enrichment -> VectorStore. */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final DocumentRegistryRepository registry;
    private final TokenTextSplitter splitter;
    private final TextSanitizer sanitizer;
    private final MimeTypeValidator mimeValidator;
    private final DocuChatProperties props;

    public DocumentIngestionService(VectorStore vectorStore, DocumentRegistryRepository registry,
                                    TokenTextSplitter splitter, TextSanitizer sanitizer,
                                    MimeTypeValidator mimeValidator, DocuChatProperties props) {
        this.vectorStore = vectorStore;
        this.registry = registry;
        this.splitter = splitter;
        this.sanitizer = sanitizer;
        this.mimeValidator = mimeValidator;
        this.props = props;
    }

    public record Result(UploadResponse response, boolean created) {}

    public Result ingest(MultipartFile file, String userId, boolean forceReindex) {
        long started = System.currentTimeMillis();

        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.ERR_EMPTY_FILE, "Uploaded file is empty.");
        }
        if (file.getSize() > props.ingestion().maxFileSize().toBytes()) {
            throw new ApiException(ErrorCode.ERR_FILE_TOO_LARGE, "File exceeds " + props.ingestion().maxFileSize() + ".");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.ERR_UNSUPPORTED_MEDIA, "Could not read uploaded file.", e);
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());
        var detection = mimeValidator.validate(bytes, fileName);
        String sha256 = sha256(bytes);

        // FR-UPL-02: content-hash deduplication
        Optional<DocumentRegistry> existing =
                registry.findFirstByUserIdAndSha256HashAndStatus(userId, sha256, DocumentStatus.INDEXED);
        if (existing.isPresent() && !forceReindex) {
            return new Result(toResponse(existing.get(), 0, started), false);
        }
        if (forceReindex) {
            registry.findByUserIdAndSha256Hash(userId, sha256).forEach(old -> {
                purgeVectors(old.getDocumentId());
                registry.delete(old);
            });
        }

        int pageCount = detection.type() == DocType.PDF ? pdfPageCount(bytes) : 1;

        DocumentRegistry row = new DocumentRegistry();
        row.setDocumentId(UUID.randomUUID());
        row.setUserId(userId);
        row.setFileName(fileName);
        row.setContentType(detection.mimeType());
        row.setFileSize(bytes.length);
        row.setSha256Hash(sha256);
        row.setPageCount(pageCount);
        row.setStatus(DocumentStatus.PROCESSING);
        row.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
        registry.save(row);

        try {
            List<Document> raw = read(bytes, fileName, detection.type());
            List<Document> clean = sanitizer.apply(raw);
            List<Document> chunks = enrich(splitter.apply(clean), row);
            if (chunks.isEmpty()) {
                throw new ApiException(ErrorCode.ERR_NO_EXTRACTABLE_TEXT, "No indexable content after chunking.");
            }
            vectorStore.add(chunks);

            row.setChunkCount(chunks.size());
            row.setStatus(DocumentStatus.INDEXED);
            registry.save(row);
            return new Result(toResponse(row, chunks.size(), started), true);
        } catch (RuntimeException e) {
            // NFR-OBS-02: purge partial chunks, mark FAILED with root cause
            safePurge(row.getDocumentId());
            row.setStatus(DocumentStatus.FAILED);
            row.setErrorMessage(truncate(rootMessage(e)));
            registry.save(row);
            throw (e instanceof ApiException) ? e : mapInfrastructureFailure(e);
        }
    }

    private List<Document> read(byte[] bytes, String fileName, DocType type) {
        Resource resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return fileName; }
        };
        if (type == DocType.PDF) {
            var config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                            .withNumberOfBottomTextLinesToDelete(0)
                            .withNumberOfTopPagesToSkipBeforeDelete(0)
                            .build())
                    .withPagesPerDocument(1) // keeps accurate page_number metadata
                    .build();
            return new PagePdfDocumentReader(resource, config).get();
        }
        return new TikaDocumentReader(resource).get();
    }

    private int pdfPageCount(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return pdf.getNumberOfPages();
        } catch (InvalidPasswordException e) {
            throw new ApiException(ErrorCode.ERR_ENCRYPTED_PDF, "PDF is password-protected and cannot be parsed.", e);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.ERR_UNSUPPORTED_MEDIA, "PDF is corrupt or unreadable.", e);
        }
    }

    /** FR-ETL-05: mandatory chunk metadata. */
    private List<Document> enrich(List<Document> chunks, DocumentRegistry row) {
        String uploadedAt = DateTimeFormatter.ISO_INSTANT.format(row.getUploadedAt().toInstant());
        List<Document> out = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document c = chunks.get(i);
            Map<String, Object> meta = new HashMap<>();
            meta.put("document_id", row.getDocumentId().toString());
            meta.put("file_name", row.getFileName());
            meta.put("user_id", row.getUserId());
            meta.put("page_number", pageOf(c));
            meta.put("chunk_index", i);
            meta.put("uploaded_at", uploadedAt);
            out.add(Document.builder()
                    .id(UUID.randomUUID().toString())  // pgvector id column is UUID
                    .text(c.getText())
                    .metadata(meta)
                    .build());
        }
        return out;
    }

    private static int pageOf(Document d) {
        Object p = d.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        return p instanceof Number n ? n.intValue() : 1;
    }

    private void purgeVectors(UUID documentId) {
        vectorStore.delete(new FilterExpressionBuilder().eq("document_id", documentId.toString()).build());
    }

    private void safePurge(UUID documentId) {
        try {
            purgeVectors(documentId);
        } catch (RuntimeException ex) {
            log.warn("Could not purge partial chunks for {}: {}", documentId, ex.getMessage());
        }
    }

    private ApiException mapInfrastructureFailure(RuntimeException e) {
        log.error("Ingestion failed", e);
        if (e instanceof DataAccessException) {
            return new ApiException(ErrorCode.ERR_VECTOR_STORE_DOWN, "Vector store is unreachable.", e);
        }
        return new ApiException(ErrorCode.ERR_EMBEDDING_PROVIDER,
                "Embedding provider or vector store failed after retries: " + rootMessage(e), e);
    }

    private UploadResponse toResponse(DocumentRegistry r, int chunks, long started) {
        return new UploadResponse(r.getDocumentId(), r.getFileName(), r.getContentType(), r.getFileSize(),
                r.getPageCount() == null ? 1 : r.getPageCount(),
                chunks > 0 ? chunks : (r.getChunkCount() == null ? 0 : r.getChunkCount()),
                props.vectorStoreProvider(), r.getStatus().name(),
                System.currentTimeMillis() - started, r.getUploadedAt());
    }

    static String sanitizeFileName(String original) {
        String n = original == null ? "upload" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[^\\w.\\- ]", "_").strip();
        return n.isEmpty() ? "upload" : n;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private static String truncate(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
