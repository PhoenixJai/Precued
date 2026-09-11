package com.precued.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3Client.builder().build() never makes a network call itself — it only
 * validates its own configuration (credentials provider, endpoint URI,
 * checksum settings) at construction time. This proves the bean wires up
 * correctly against an R2-shaped account id with no real network access or
 * real credentials required; it does not prove R2 is actually reachable
 * with real credentials, which needs live verification once those exist.
 */
class StorageConfigTest {

    @Test
    void contextLoads_withR2ShapedAccountId() {
        new ApplicationContextRunner()
                .withUserConfiguration(StorageConfig.class)
                .withPropertyValues(
                        "precued.storage.r2.account-id=abc123def456",
                        "precued.storage.r2.access-key-id=test-access-key",
                        "precued.storage.r2.secret-access-key=test-secret-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3Client.class);
                });
    }

    /**
     * Regression test for a real failure this caught: before the bean was
     * marked {@code @Lazy}, a blank account-id (application.yml's own
     * documented default before R2 is configured — see .env.example) threw
     * at eager singleton creation, failing the WHOLE application context —
     * confirmed via LiveKitTokenIntegrationTest's real Spring Boot context
     * failing to start with this exact cause, not assumed. Context must
     * boot cleanly with the empty defaults every environment starts with.
     */
    @Test
    void contextLoads_withBlankAccountId_becauseTheBeanIsLazy() {
        new ApplicationContextRunner()
                .withUserConfiguration(StorageConfig.class)
                .withPropertyValues(
                        "precued.storage.r2.account-id=",
                        "precued.storage.r2.access-key-id=",
                        "precued.storage.r2.secret-access-key=")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
