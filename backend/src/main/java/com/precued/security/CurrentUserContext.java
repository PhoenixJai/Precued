package com.precued.security;

import com.precued.entity.User;

import java.util.Optional;

/**
 * Holds the User resolved from the current request's AuthSession bearer
 * token, set by {@link AuthSessionInterceptor} and cleared at the end of
 * every request. Unlike {@link CurrentParticipantContext}, a request can
 * legitimately have no User here at all (a guest join, or any endpoint
 * outside AuthSessionInterceptor's paths) — {@link #getIfPresent()} is how
 * a caller that must tolerate that checks; {@link #get()} is for callers on
 * a path where AuthSessionInterceptor guarantees one is always set.
 */
public final class CurrentUserContext {

    private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

    private CurrentUserContext() {}

    public static void set(User user) {
        CURRENT.set(user);
    }

    /** @throws IllegalStateException if called where AuthSessionInterceptor doesn't guarantee a User. */
    public static User get() {
        User user = CURRENT.get();
        if (user == null) {
            throw new IllegalStateException(
                    "No authenticated User on this request — AuthSessionInterceptor didn't run, no"
                            + " Authorization header was sent, or this path doesn't require one");
        }
        return user;
    }

    public static Optional<User> getIfPresent() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
