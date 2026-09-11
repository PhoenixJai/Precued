package com.precued.service;

/**
 * A PDF upload was rejected for a reason the caller can fix (wrong file
 * type, too large, too many pages, corrupt/malformed PDF, zero pages) —
 * mapped to 400 Bad Request by GlobalExceptionHandler, distinct from
 * IllegalArgumentException (404, "no such entity") and IllegalStateException
 * (403, authorization) used elsewhere in this service layer.
 */
public class PresentationUploadException extends RuntimeException {

    public PresentationUploadException(String message) {
        super(message);
    }

    public PresentationUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
