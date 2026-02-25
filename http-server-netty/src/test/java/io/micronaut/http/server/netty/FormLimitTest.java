package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormLimitTest {
    @Test
    public void fieldMaxBufferedBytes_completed_success() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "FormLimitTest",
            "micronaut.server.netty.field-max-buffered-bytes", "12",
            "micronaut.http.client.exception-on-error-status", false
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class)) {
            server.start();

            try (BlockingHttpClient client = ctx.createBean(HttpClient.class, server.getURI()).toBlocking()) {
                assertEquals(
                    "{\"foo\":\"mylengthis12\",\"hello\":\"mylengthis12\"}",
                    client.retrieve(HttpRequest.POST("/form-limit/completed", MultipartBody.builder()
                        .addPart("foo", "mylengthis12")
                        .addPart("hello", "mylengthis12")
                        .build()).contentType(MediaType.MULTIPART_FORM_DATA), Argument.STRING, Argument.STRING)
                );
            }
        }
    }

    @Test
    public void fieldMaxBufferedBytes_completed_fail() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "FormLimitTest",
            "micronaut.server.netty.field-max-buffered-bytes", "5",
            "micronaut.http.client.exception-on-error-status", false
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class)) {
            server.start();

            try (BlockingHttpClient client = ctx.createBean(HttpClient.class, server.getURI()).toBlocking()) {
                assertEquals(
                    "{\"message\":\"Request Entity Too Large\",\"_embedded\":{\"errors\":[{\"message\":\"The content length [12] exceeds the maximum allowed bufferable length [5]. Note that the maximum buffer size got its own configuration property (micronaut.server.max-request-buffer-size) in 4.5.0 that you may have to configure. Alternatively you can rewrite your controller to stream the request instead of buffering it.\"}]},\"_links\":{\"self\":{\"href\":\"/form-limit/completed\",\"templated\":false}}}",
                    client.retrieve(HttpRequest.POST("/form-limit/completed", MultipartBody.builder()
                        .addPart("foo", "mylengthis12")
                        .addPart("hello", "mylengthis12")
                        .build()).contentType(MediaType.MULTIPART_FORM_DATA), Argument.STRING, Argument.STRING)
                );
            }
        }
    }

    @Test
    public void formMaxBufferedBytes_completed_success() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "FormLimitTest",
            "micronaut.server.netty.form-max-buffered-bytes", "12",
            "micronaut.http.client.exception-on-error-status", false
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class)) {
            server.start();

            try (BlockingHttpClient client = ctx.createBean(HttpClient.class, server.getURI()).toBlocking()) {
                assertEquals(
                    "{\"foo\":\"55555\",\"hello\":\"7777777\"}",
                    client.retrieve(HttpRequest.POST("/form-limit/completed", MultipartBody.builder()
                        .addPart("foo", "55555")
                        .addPart("hello", "7777777")
                        .build()).contentType(MediaType.MULTIPART_FORM_DATA), Argument.STRING, Argument.STRING)
                );
            }
        }
    }

    @Test
    public void formMaxBufferedBytes_completed_fail() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "FormLimitTest",
            "micronaut.server.netty.form-max-buffered-bytes", "12",
            "micronaut.http.client.exception-on-error-status", false
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class)) {
            server.start();

            try (BlockingHttpClient client = ctx.createBean(HttpClient.class, server.getURI()).toBlocking()) {
                assertEquals(
                    "{\"message\":\"Request Entity Too Large\",\"_embedded\":{\"errors\":[{\"message\":\"The content length [13] exceeds the maximum allowed bufferable length [12]. Note that the maximum buffer size got its own configuration property (micronaut.server.max-request-buffer-size) in 4.5.0 that you may have to configure. Alternatively you can rewrite your controller to stream the request instead of buffering it.\"}]},\"_links\":{\"self\":{\"href\":\"/form-limit/completed\",\"templated\":false}}}",
                    client.retrieve(HttpRequest.POST("/form-limit/completed", MultipartBody.builder()
                        .addPart("foo", "666666")
                        .addPart("hello", "7777777")
                        .build()).contentType(MediaType.MULTIPART_FORM_DATA), Argument.STRING, Argument.STRING)
                );
            }
        }
    }

    private static @NonNull HttpURLConnection openConnection(EmbeddedServer server, String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(server.getURL() + path).toURL().openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + "boundary");
        conn.setRequestProperty("Accept", MediaType.APPLICATION_JSON);
        return conn;
    }

    @Requires(property = "spec.name", value = "FormLimitTest")
    @Controller("/form-limit")
    static final class MyController {
        @Post("/completed")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public Map<?, ?> completed(CompletedAttribute foo, CompletedAttribute hello) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("foo", foo.toReadBuffer().toString(StandardCharsets.UTF_8));
            map.put("hello", hello.toReadBuffer().toString(StandardCharsets.UTF_8));
            return map;
        }
    }
}
