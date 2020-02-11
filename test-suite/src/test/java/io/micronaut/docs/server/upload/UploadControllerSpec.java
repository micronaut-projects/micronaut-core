package io.micronaut.docs.server.upload;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import io.reactivex.Flowable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class UploadControllerSpec {

    private static EmbeddedServer server;
    private static RxHttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                .getApplicationContext()
                .createBean(RxHttpClient.class, server.getURL());
    }

    @AfterClass
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
        try {
            File file = File.createTempFile("file.json", "temp");
            file.delete();
        } catch (IOException e) { }
    }

    @Test
    public void testFileUpload() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".getBytes())
                .build();

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));
        HttpResponse<String> response = flowable.blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Uploaded", response.getBody().get());
    }

    @Test
    public void testCompletedFileUpload() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".getBytes())
                .build();

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));
        HttpResponse<String> response = flowable.blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Uploaded", response.getBody().get());
    }

    @Test
    public void testCompletedFileUploadWithFilenameButNoBytes() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build();

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));
        HttpResponse<String> response = flowable.blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Uploaded", response.getBody().get());
    }

    @Test
    public void testCompletedFileUploadNoNameWithBytes() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".getBytes())
                .build();

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> flowable.blockingFirst());

        assertEquals("Required argument [CompletedFileUpload file] not specified", ex.getMessage());
    }

    @Test
    public void testCompletedFileUploadWithNoFileNameAndNoBytes() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build();

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> flowable.blockingFirst());

        assertEquals("Required argument [CompletedFileUpload file] not specified", ex.getMessage());
    }

    @Test
    public void testCompletedFileUploadWithNoPart() {
        MultipartBody body = MultipartBody.builder()
                .addPart("filex", "", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build();

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> flowable.blockingFirst());

        assertEquals("Required argument [CompletedFileUpload file] not specified", ex.getMessage());
    }

    @Test
    public void testFileBytesUpload() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.TEXT_PLAIN_TYPE, "some data".getBytes())
                .addPart("fileName", "bar")
                .build();

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/bytes", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));
        HttpResponse<String> response = flowable.blockingFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Uploaded", response.getBody().get());
    }
}
