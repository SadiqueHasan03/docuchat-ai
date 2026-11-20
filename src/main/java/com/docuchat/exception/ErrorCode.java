package com.docuchat.exception;

import org.springframework.http.HttpStatus;

/** SRS 5.3 error taxonomy (+ ERR_LLM_PROVIDER, ERR_NOT_FOUND). */
public enum ErrorCode {
    ERR_EMPTY_FILE(HttpStatus.BAD_REQUEST),
    ERR_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    ERR_UNSUPPORTED_MEDIA(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    ERR_ENCRYPTED_PDF(HttpStatus.UNPROCESSABLE_ENTITY),
    ERR_NO_EXTRACTABLE_TEXT(HttpStatus.UNPROCESSABLE_ENTITY),
    ERR_EMBEDDING_PROVIDER(HttpStatus.BAD_GATEWAY),
    ERR_LLM_PROVIDER(HttpStatus.BAD_GATEWAY),
    ERR_VECTOR_STORE_DOWN(HttpStatus.SERVICE_UNAVAILABLE),
    ERR_NOT_FOUND(HttpStatus.NOT_FOUND),
    ERR_EMAIL_IN_USE(HttpStatus.CONFLICT),
    ERR_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ERR_VALIDATION(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
    public HttpStatus status() { return status; }
}
