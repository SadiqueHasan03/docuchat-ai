package com.docuchat.service;

import com.docuchat.exception.ApiException;
import com.docuchat.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

/** FR-ETL-03: null-byte stripping, whitespace/hyphenation cleanup, and scanned-PDF detection. */
@Component
public class TextSanitizer implements DocumentTransformer {

    static final int MIN_ALPHANUMERIC = 20;

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> out = new ArrayList<>();
        long alnum = 0;
        for (Document d : documents) {
            String clean = clean(d.getText());
            if (clean.isBlank()) continue;
            alnum += clean.chars().filter(Character::isLetterOrDigit).count();
            out.add(d.mutate().text(clean).build());
        }
        if (alnum < MIN_ALPHANUMERIC) {
            throw new ApiException(ErrorCode.ERR_NO_EXTRACTABLE_TEXT,
                    "No extractable text found; the file may be a scanned image-only PDF (OCR is out of scope for v2.0).");
        }
        return out;
    }

    static String clean(String text) {
        if (text == null) return "";
        return text
                .replace("\u0000", "")
                .replaceAll("(\\p{L})-[ \\t]*\\r?\\n[ \\t]*(\\p{L})", "$1$2")  // broken hyphenated line breaks
                .replaceAll("[ \\t\\x0B\\f]+", " ")                             // collapse spaces/tabs
                .replaceAll("\\s*\\r?\\n\\s*(\\r?\\n\\s*)+", "\n\n")            // collapse blank-line runs
                .strip();
    }
}
