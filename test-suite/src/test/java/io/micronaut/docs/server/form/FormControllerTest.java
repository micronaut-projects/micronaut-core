package io.micronaut.docs.server.form;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "FormControllerTest")
@MicronautTest
public class FormControllerTest {

    @Test
    void testString(@Client("/") HttpClient httpClient) {
        assertEquals("New user name for user ID 5: yawkat", httpClient.toBlocking().retrieve((HttpRequest<?>) HttpRequest.POST("/form/string", MultipartBody.builder()
            .addPart("userId", "5")
            .addPart("userName", "yawkat")
            .build()).contentType(MediaType.MULTIPART_FORM_DATA)));
    }

    @Test
    void testFileUpload(@Client("/") HttpClient httpClient) {
        assertEquals("Uploaded avatar for user 5: 16 bytes", httpClient.toBlocking().retrieve((HttpRequest<?>) HttpRequest.POST("/form/file-upload", MultipartBody.builder()
            .addPart("userId", "5")
            .addPart("avatar", "avatar.png", new byte[16])
            .build()).contentType(MediaType.MULTIPART_FORM_DATA)));
    }

    @Test
    void testStreamingFileUpload(@Client("/") HttpClient httpClient) {
        assertEquals("Streamed avatar for user 6: 8 bytes", httpClient.toBlocking().retrieve((HttpRequest<?>) HttpRequest.POST("/form/file-upload-streaming", MultipartBody.builder()
            .addPart("userId", "6")
            .addPart("avatar", "avatar.png", new byte[8])
            .build()).contentType(MediaType.MULTIPART_FORM_DATA)));
    }

    @Test
    void testPublisherCompletedFileUpload(@Client("/") HttpClient httpClient) {
        assertEquals("Uploaded avatar for user 7: 10 bytes", httpClient.toBlocking().retrieve((HttpRequest<?>) HttpRequest.POST("/form/file-upload-completed-publisher", MultipartBody.builder()
            .addPart("userId", "7")
            .addPart("avatar", "a1.png", new byte[10])
            .build()).contentType(MediaType.MULTIPART_FORM_DATA)));
    }

    @Test
    void testPublisherStreamingFileUpload(@Client("/") HttpClient httpClient) {
        assertEquals("Streamed avatar for user 8: 15 bytes", httpClient.toBlocking().retrieve((HttpRequest<?>) HttpRequest.POST("/form/file-upload-streaming-publisher", MultipartBody.builder()
            .addPart("userId", "8")
            .addPart("avatar", "a1.png", new byte[15])
            .build()).contentType(MediaType.MULTIPART_FORM_DATA)));
    }

    @Test
    void testPublisherPublisher(@Client("/") HttpClient httpClient) {
        assertEquals("Streamed avatars for user 8: [15, 30] bytes", httpClient.toBlocking().retrieve((HttpRequest<?>) HttpRequest.POST("/form/publisher-publisher", MultipartBody.builder()
            .addPart("userId", "8")
            .addPart("avatar", "a1.png", new byte[15])
            .addPart("avatar", "a2.png", new byte[30])
            .build()).contentType(MediaType.MULTIPART_FORM_DATA)));
    }

    @Test
    void testPublisherPartData(@Client("/") HttpClient httpClient) {
        assertEquals("Streamed avatars for user 8: 45 bytes", httpClient.toBlocking().retrieve((HttpRequest<?>) HttpRequest.POST("/form/publisher-part-data", MultipartBody.builder()
            .addPart("userId", "8")
            .addPart("avatar", "a1.png", new byte[15])
            .addPart("avatar", "a2.png", new byte[30])
            .build()).contentType(MediaType.MULTIPART_FORM_DATA)));
    }
}
