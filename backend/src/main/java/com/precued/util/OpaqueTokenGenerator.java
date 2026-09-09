package com.precued.util;

import java.security.SecureRandom;
import java.util.Base64;

/** Shared opaque, random, URL-safe token generation — used anywhere a bearer credential is needed. */
public final class OpaqueTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OpaqueTokenGenerator() {}

    public static String generate() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
