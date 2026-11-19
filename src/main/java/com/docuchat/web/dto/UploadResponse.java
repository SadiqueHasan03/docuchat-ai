package com.docuchat.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UploadResponse(UUID documentId, String fileName, String contentType, long fileSizeBytes,
                             int pageCount, int chunkCount, String vectorStoreProvider, String status,
                             long processingTimeMs, OffsetDateTime uploadedAt) {}
