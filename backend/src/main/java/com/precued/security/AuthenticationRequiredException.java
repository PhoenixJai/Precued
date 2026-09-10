package com.precued.security;

/**
 * A request needed proof of User identity (a valid AuthSession bearer
 * token) and didn't have one — distinct from {@link IllegalStateException}
 * ("well-formed request, rejected on a business rule"), since this is
 * "we don't know who you are" rather than "we know who you are and you
 * can't do that." Mapped to 401 by GlobalExceptionHandler. Thrown from a
 * service, not an interceptor, when whether authentication was required at
 * all depends on the request body (see RoomParticipantService#join) — the
 * interceptor can't decide that itself, since it runs before the body is
 * parsed.
 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
