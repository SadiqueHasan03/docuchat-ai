package com.docuchat.service;

import com.docuchat.domain.DocumentRegistry;
import com.docuchat.domain.DocumentRegistryRepository;
import com.docuchat.exception.ApiException;
import com.docuchat.exception.ErrorCode;
import com.docuchat.web.dto.DocumentSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** FR-UPL-03: listing and cascading deletion. */
@Service
public class DocumentService {

    private final DocumentRegistryRepository registry;
    private final VectorStore vectorStore;

    public DocumentService(DocumentRegistryRepository registry, VectorStore vectorStore) {
        this.registry = registry;
        this.vectorStore = vectorStore;
    }

    public List<DocumentSummary> list(String userId) {
        return registry.findByUserIdOrderByUploadedAtDesc(userId).stream().map(this::toSummary).toList();
    }

    /** Vectors first, then the registry row; if the vector delete fails the row stays so the user can retry. */
    public void delete(UUID documentId, String userId) {
        DocumentRegistry doc = registry.findByDocumentIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ERR_NOT_FOUND, "Document not found."));
        try {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            vectorStore.delete(b.and(b.eq("document_id", documentId.toString()), b.eq("user_id", userId)).build());
        } catch (DataAccessException e) {
            throw new ApiException(ErrorCode.ERR_VECTOR_STORE_DOWN, "Vector store is unreachable.", e);
        }
        registry.delete(doc);
    }

    private DocumentSummary toSummary(DocumentRegistry r) {
        return new DocumentSummary(r.getDocumentId(), r.getFileName(), r.getContentType(), r.getFileSize(),
                r.getPageCount() == null ? 0 : r.getPageCount(),
                r.getChunkCount() == null ? 0 : r.getChunkCount(),
                r.getStatus().name(), r.getErrorMessage(), r.getUploadedAt());
    }
}
