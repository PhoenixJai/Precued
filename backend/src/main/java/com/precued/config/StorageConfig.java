package com.precued.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Cloudflare R2 is S3-compatible: same API, different endpoint
 * (https://&lt;account-id&gt;.r2.cloudflarestorage.com) and no real AWS region
 * ("auto"). Two R2-specific quirks, not optional:
 * - pathStyleAccessEnabled(true): R2 doesn't support AWS's virtual-hosted
 *   bucket-subdomain addressing.
 * - requestChecksumCalculation/responseChecksumValidation(WHEN_REQUIRED):
 *   AWS SDK v2 2.30.0+ defaults to sending a CRC32 checksum header R2
 *   rejects outright ("Header 'x-amz-checksum-algorithm' ... not
 *   implemented") — confirmed against the SDK's own changelog/community
 *   reports, not assumed. Without this override, every request fails.
 *
 * {@code @Lazy}: a blank account-id (the expected state before R2 is
 * provisioned/configured — see .env.example) makes endpointOverride's own
 * URI sanitization throw (confirmed via a real failing integration-test
 * boot, not assumed: an authority starting with "." parses with
 * getHost() == null, and AWS SDK's StaticClientEndpointProvider re-derives
 * the URI from scheme+host+port, producing the invalid, authority-less
 * "https:"). Without @Lazy that throws at eager singleton creation during
 * context refresh — the ENTIRE application fails to boot, taking down
 * every unrelated feature (rooms, calls, auth) over a presentations-only
 * dependency that may simply not be configured yet. @Lazy defers real
 * construction to first actual use, so the rest of the app boots and runs
 * fine; only a presentation upload/fetch request fails, with this same
 * clear cause, until R2 is configured.
 */
@Configuration
public class StorageConfig {

    @Bean
    @Lazy
    public S3Client r2Client(
            @Value("${precued.storage.r2.account-id}") String accountId,
            @Value("${precued.storage.r2.access-key-id}") String accessKeyId,
            @Value("${precued.storage.r2.secret-access-key}") String secretAccessKey) {
        return S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
