package com.docuchat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docuchat.exception.ApiException;
import com.docuchat.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class TextSanitizerTest {

    private final TextSanitizer sanitizer = new TextSanitizer();

    @Test
    void stripsNullBytesAndCollapsesWhitespace() {
        assertThat(TextSanitizer.clean("Hello\u0000   world\t\tagain\n\n\n\nNext"))
                .isEqualTo("Hello world again\n\nNext");
    }

    @Test
    void repairsHyphenatedLineBreaks() {
        assertThat(TextSanitizer.clean("retrie-\nval works")).isEqualTo("retrieval works");
    }

    @Test
    void rejectsDocumentsWithoutExtractableText() {
        var docs = List.of(new Document("  --  \u0000 "), new Document("..."));
        assertThatThrownBy(() -> sanitizer.apply(docs))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ERR_NO_EXTRACTABLE_TEXT));
    }

    @Test
    void keepsNormalDocuments() {
        var docs = List.of(new Document("This page has plenty of real, selectable text on it."));
        assertThat(sanitizer.apply(docs)).hasSize(1);
    }
}
