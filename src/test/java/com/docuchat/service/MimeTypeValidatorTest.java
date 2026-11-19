package com.docuchat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docuchat.exception.ApiException;
import com.docuchat.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MimeTypeValidatorTest {

    private final MimeTypeValidator validator = new MimeTypeValidator();

    @Test
    void acceptsPlainText() {
        var d = validator.validate("hello world".getBytes(StandardCharsets.UTF_8), "notes.txt");
        assertThat(d.type()).isEqualTo(DocType.TEXT);
    }

    @Test
    void rejectsPdfDisguisedAsText() {
        byte[] fakePdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> validator.validate(fakePdf, "evil.txt"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ERR_UNSUPPORTED_MEDIA));
    }

    @Test
    void rejectsUnknownExtension() {
        assertThatThrownBy(() -> validator.validate(new byte[]{1, 2, 3}, "a.exe"))
                .isInstanceOf(ApiException.class);
    }
}
