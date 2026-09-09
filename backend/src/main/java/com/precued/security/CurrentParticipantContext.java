package com.precued.security;

import com.precued.entity.RoomParticipant;

/**
 * Holds the RoomParticipant resolved from the current request's session
 * token, set by {@link ParticipantSessionInterceptor} and cleared at the end
 * of every request. Services use this to check resource ownership (e.g.
 * "is the caller the Share's publisher?") for writes the interceptor can't
 * check itself, since it runs before the request body is parsed.
 */
public final class CurrentParticipantContext {

    private static final ThreadLocal<RoomParticipant> CURRENT = new ThreadLocal<>();

    private CurrentParticipantContext() {}

    public static void set(RoomParticipant participant) {
        CURRENT.set(participant);
    }

    /** @throws IllegalStateException if called outside a request the interceptor authenticated. */
    public static RoomParticipant get() {
        RoomParticipant participant = CURRENT.get();
        if (participant == null) {
            throw new IllegalStateException(
                    "No authenticated RoomParticipant on this request — ParticipantSessionInterceptor"
                            + " didn't run or this path is excluded from it");
        }
        return participant;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
