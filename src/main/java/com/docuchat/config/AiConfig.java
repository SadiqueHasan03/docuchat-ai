package com.docuchat.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /** FR-ETL-04: token-aware chunking (jtokkit CL100K_BASE). */
    @Bean
    TokenTextSplitter tokenTextSplitter(DocuChatProperties props) {
        var i = props.ingestion();
        return TokenTextSplitter.builder()
                .withChunkSize(i.chunkSize())
                .withMinChunkSizeChars(i.minChunkSizeChars())
                .withMinChunkLengthToEmbed(i.minChunkLengthToEmbed())
                .withMaxNumChunks(i.maxNumChunks())
                .withKeepSeparator(true)
                .build();
    }

    /** FR-VEC-02: sub-batches respecting provider limits (8,191 tokens, 10% reserve). */
    @Bean
    BatchingStrategy batchingStrategy() {
        return new TokenCountBatchingStrategy();
    }

    /** FR-RAG-02: sliding window of the last N messages per conversation. */
    @Bean
    ChatMemory chatMemory(DocuChatProperties props) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(props.rag().memoryWindow())
                .build();
    }
}
