package com.precued.controller;

import com.precued.security.AuthenticationRequiredException;
import com.precued.security.EmailAlreadyRegisteredException;
import com.precued.security.InvalidCredentialsException;
import com.precued.service.PresentationUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Every service in com.precued.service throws IllegalArgumentException for
 * "no such <Entity> with id X" and IllegalStateException for a business-rule
 * rejection (e.g. a publisher without a host role) — mapped centrally here
 * so no controller needs its own try/catch. Bodies are RFC 7807 ProblemDetail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** "We don't know who you are" — a body field claimed a User identity with no AuthSession to back it. */
    @ExceptionHandler(AuthenticationRequiredException.class)
    public ProblemDetail handleAuthenticationRequired(AuthenticationRequiredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** Signup with an email that's already registered. */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Login with a wrong password, unknown email, or an email with no password set — same response either way. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** The request is well-formed but violates a business rule (e.g. missing host role). */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleForbidden(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    /** A PDF upload rejected for a reason the caller can fix — bad file type, too large, too many pages, corrupt. */
    @ExceptionHandler(PresentationUploadException.class)
    public ProblemDetail handleBadUpload(PresentationUploadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Defense in depth beneath PresentationUploadService's own size check — see application.yml's multipart cap. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "File exceeds the maximum upload size");
    }
}
