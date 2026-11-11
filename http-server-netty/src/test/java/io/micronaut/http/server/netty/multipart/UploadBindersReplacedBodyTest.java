package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The upload binders bind the body a filter set on its copy of the request, not the multipart
 * body of the request.
 */
class UploadBindersReplacedBodyTest {
    private static final String SPEC_NAME = "UploadBindersReplacedBodyTest";
    private static final String BOUNDARY = "XyZb";

    @ParameterizedTest
    @CsvSource({
        "none, completed, completed:upload.txt:uploaded",
        "clear, completed, completed:null",
        "replace, completed, completed:null",
        "none, streaming, streaming:upload.txt",
        "clear, streaming, streaming:null",
        "replace, streaming, streaming:null",
        "none, parts, parts:1",
        "clear, parts, parts:null",
        "replace, parts, parts:null",
        "none, multipart, multipart:1",
        "clear, multipart, multipart:null",
        "replace, multipart, multipart:null",
        "none, raw, raw:upload.txt",
        "clear, raw, raw:null",
        "replace, raw, raw:null",
    })
    void uploadBindersFollowTheBodySetByAFilter(String mode, String route, String expected) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
             HttpClient client = HttpClient.newHttpClient()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String body = "--" + BOUNDARY + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"upload.txt\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n" +
                "uploaded\r\n" +
                "--" + BOUNDARY + "--\r\n";
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/upload-binders/" + route))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("X-Body", mode)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), BodyHandlers.ofString());
            assertEquals(expected, response.body(), "status " + response.statusCode());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Filters implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.serverFilter("/upload-binders/**").beforeReplacing(request -> switch (String.valueOf(request.getHeaders().get("X-Body"))) {
                case "clear" -> request.body(null);
                case "replace" -> request.body(Map.of("file", "replaced"));
                default -> request;
            });
        }
    }

    @Controller("/upload-binders")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Route {
        @Post(value = "/completed", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        String completed(@Nullable CompletedFileUpload file) throws IOException {
            return "completed:" + (file == null ? "null" : file.getFilename() + ":" + new String(file.getBytes(), StandardCharsets.UTF_8));
        }

        @Post(value = "/streaming", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Publisher<String> streaming(@Nullable StreamingFileUpload file) {
            if (file == null) {
                return Mono.just("streaming:null");
            }
            return Mono.from(file.transferTo(new ByteArrayOutputStream())).then(Mono.just("streaming:" + file.getFilename()));
        }

        @Post(value = "/parts", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Publisher<String> parts(@Nullable Publisher<PartData> file) {
            if (file == null) {
                return Mono.just("parts:null");
            }
            return Flux.from(file).doOnNext(PartData::getBytes).count().map(c -> "parts:" + c);
        }

        @Post(value = "/multipart", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Publisher<String> multipart(@Nullable @Body MultipartBody body) {
            if (body == null) {
                return Mono.just("multipart:null");
            }
            return Flux.from(body).count().map(c -> "multipart:" + c);
        }

        @Post(value = "/raw", produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        String raw(@Nullable RawFormField file) {
            if (file == null) {
                return "raw:null";
            }
            try (file) {
                return "raw:" + file.metadata().fileName();
            }
        }
    }
}
