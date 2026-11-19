package com.docuchat.web.dto;

import java.util.List;

public record ChatAnswer(String conversationId, String answer, List<Source> sources, Usage usage, long latencyMs) {

    public record Source(String documentId, String fileName, int pageNumber, int chunkIndex,
                         Double similarityScore, String snippet) {}

    public record Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {}
}
