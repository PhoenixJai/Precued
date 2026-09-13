package com.precued.security;

/** Signup with an email that already has a User row. Mapped to 409 by GlobalExceptionHandler. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
