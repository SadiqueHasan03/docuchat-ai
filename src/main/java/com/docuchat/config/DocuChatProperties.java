package com.docuchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "docuchat")
public record DocuChatProperties(String vectorStoreProvider, Security security, Ingestion ingestion, Rag rag) {

    public record Security(String jwtSecret) {}

    public record Ingestion(DataSize maxFileSize, int chunkSize, int minChunkSizeChars,
                            int minChunkLengthToEmbed, int maxNumChunks) {}

    public record Rag(int defaultTopK, int maxTopK, double similarityThreshold,
                      int memoryWindow, boolean rewriteQueryEnabled) {}
}
