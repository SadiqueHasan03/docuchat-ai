package com.docuchat.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;

/** Note: user_id is deliberately absent (NFR-SEC-01). */
public record ChatRequest(String conversationId,
                          String question,
                          List<UUID> documentIds,
                          @Min(1) @Max(20) Integer topK,
                          @DecimalMin("0.0") @DecimalMax("1.0") Double similarityThreshold) {}
