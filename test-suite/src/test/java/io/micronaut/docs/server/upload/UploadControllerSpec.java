/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.server.upload;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class UploadControllerSpec {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                .getApplicationContext()
                .createBean(HttpClient.class, server.getURL());
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

        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));
        HttpResponse<String> response = flowable.blockFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Uploaded", response.getBody().get());
    }

    @Test
    public void testCompletedFileUpload() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".getBytes())
                .build();

        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));
        HttpResponse<String> response = flowable.blockFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Uploaded", response.getBody().get());
    }

    @Test
    public void testCompletedFileUploadWithFilenameButNoBytes() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build();

        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));
        HttpResponse<String> response = flowable.blockFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Uploaded", response.getBody().get());
    }

    @Test
    public void testCompletedFileUploadNoNameWithBytes() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".getBytes())
                .build();

        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> flowable.blockFirst());
        Map<String, Object> embedded = (Map<String, Object>) ex.getResponse().getBody(Map.class).get().get("_embedded");
        Object message = ((Map<String, Object>) ((List) embedded.get("errors")).get(0)).get("message");

        assertEquals("Required argument [CompletedFileUpload file] not specified", message);
    }

    @Test
    public void testCompletedFileUploadWithNoFileNameAndNoBytes() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build();

        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> flowable.blockFirst());
        Map<String, Object> embedded = (Map<String, Object>) ex.getResponse().getBody(Map.class).get().get("_embedded");
        Object message = ((Map<String, Object>) ((List) embedded.get("errors")).get(0)).get("message");

        assertEquals("Required argument [CompletedFileUpload file] not specified", message);
    }

    @Test
    public void testCompletedFileUploadWithNoPart() {
        MultipartBody body = MultipartBody.builder()
                .addPart("filex", "", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build();

        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));

        HttpClientResponseException ex = Assertions.assertThrows(HttpClientResponseException.class, () -> flowable.blockFirst());
        Map<String, Object> embedded = (Map<String, Object>) ex.getResponse().getBody(Map.class).get().get("_embedded");
        Object message = ((Map<String, Object>) ((List) embedded.get("errors")).get(0)).get("message");

        assertEquals("Required argument [CompletedFileUpload file] not specified", message);
    }

    @Test
    public void testFileBytesUpload() {
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.TEXT_PLAIN_TYPE, "some data".getBytes())
                .addPart("fileName", "bar")
                .build();

        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/bytes", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String.class
        ));
        HttpResponse<String> response = flowable.blockFirst();

        assertEquals(HttpStatus.OK.getCode(), response.code());
        assertEquals("Uploaded", response.getBody().get());
    }
}
