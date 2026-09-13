package com.precued.security;

/**
 * Login with an unknown email, a wrong password, or an email that has no
 * password set at all (a magic-link-created User). Same message and status
 * for all three cases — never reveals which one it was. Mapped to 401 by
 * GlobalExceptionHandler.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
