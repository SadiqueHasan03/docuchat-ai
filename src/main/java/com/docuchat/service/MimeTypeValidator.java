package com.docuchat.service;

import com.docuchat.exception.ApiException;
import com.docuchat.exception.ErrorCode;
import java.util.Locale;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/** FR-UPL-01: true MIME verification via Tika magic bytes, not just the extension. */
@Component
public class MimeTypeValidator {

    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final Tika tika = new Tika();

    public record Detection(DocType type, String mimeType) {}

    public Detection validate(byte[] bytes, String fileName) {
        String ext = extension(fileName);
        String mime = tika.detect(bytes, fileName);

        return switch (ext) {
            case "pdf" -> require("application/pdf".equals(mime), DocType.PDF, "application/pdf", mime, ext);
            case "docx" -> require(DOCX.equals(mime) || "application/x-tika-ooxml".equals(mime),
                    DocType.DOCX, DOCX, mime, ext);
            case "doc" -> require("application/msword".equals(mime) || "application/x-tika-msoffice".equals(mime),
                    DocType.DOC, "application/msword", mime, ext);
            case "txt", "md" -> require(mime != null && mime.startsWith("text/"), DocType.TEXT,
                    "md".equals(ext) ? "text/markdown" : "text/plain", mime, ext);
            default -> throw unsupported(mime, ext);
        };
    }

    private Detection require(boolean ok, DocType type, String canonicalMime, String detected, String ext) {
        if (!ok) throw unsupported(detected, ext);
        return new Detection(type, canonicalMime);
    }

    private ApiException unsupported(String mime, String ext) {
        return new ApiException(ErrorCode.ERR_UNSUPPORTED_MEDIA,
                "File content (" + mime + ") does not match a supported type (.pdf, .docx, .doc, .txt, .md) for extension '." + ext + "'.");
    }

    static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
