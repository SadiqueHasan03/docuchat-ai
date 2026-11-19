package com.docuchat.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSummary(UUID documentId, String fileName, String contentType, long fileSizeBytes,
                              int pageCount, int chunkCount, String status, String errorMessage,
                              OffsetDateTime uploadedAt) {}
