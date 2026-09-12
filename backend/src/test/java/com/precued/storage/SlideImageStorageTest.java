package com.precued.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlideImageStorageTest {

    private static final String BUCKET = "test-bucket";

    @Mock private S3Client s3Client;

    @Test
    void upload_putsObjectUnderGivenKeyWithContentType() {
        SlideImageStorage storage = new SlideImageStorage(s3Client, BUCKET);
        byte[] content = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

        storage.upload("shares/abc/slides/0.png", content, "image/png");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo("shares/abc/slides/0.png");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void download_returnsBytesReadFromTheObjectStream() {
        SlideImageStorage storage = new SlideImageStorage(s3Client, BUCKET);
        byte[] content = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(content)));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        byte[] result = storage.download("shares/abc/slides/0.png");

        assertThat(result).isEqualTo(content);
    }

    @Test
    void download_noSuchKey_throwsClearIllegalArgumentExceptionRatherThanLeakingTheSdkException() {
        SlideImageStorage storage = new SlideImageStorage(s3Client, BUCKET);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("The specified key does not exist.").build());

        assertThatThrownBy(() -> storage.download("shares/abc/slides/6.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shares/abc/slides/6.png");
    }
}
