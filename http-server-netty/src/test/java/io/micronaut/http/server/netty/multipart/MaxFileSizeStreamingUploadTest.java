package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code max-file-size} applies to every upload binding, not only to buffered uploads.
 */
class MaxFileSizeStreamingUploadTest {
    private static final String SPEC_NAME = "MaxFileSizeStreamingUploadTest";

    /**
     * Max-file-size is only checked when an upload is buffered, not for
     * streamed uploads.
     */
    @ParameterizedTest
    @ValueSource(strings = {"completed", "streaming-publisher", "part-data", "streaming"})
    void maxFileSizeAppliesToEveryUploadBinding(String route) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.multipart.max-file-size", 1024))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String body = "--boundary\r\nContent-Disposition: form-data; name=\"avatar\"; filename=\"a.bin\"\r\n\r\n"
                + "A".repeat(8 * 1024) + "\r\n--boundary--\r\n";
            String response = RawHttp.exchange(server.getPort(), 5000, RawHttp.withBody(
                "POST /g11/" + route + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: multipart/form-data; boundary=boundary\r\n",
                body.getBytes(StandardCharsets.ISO_8859_1)));
            assertTrue(response.startsWith("HTTP/1.1 413"), "an 8 KiB file over a 1 KiB max-file-size was accepted by /" + route + ": " + response.lines().findFirst().orElse("") + " " + tail(response));
        }
    }

    private static String tail(String response) {
        int i = response.indexOf("\r\n\r\n");
        return i < 0 ? "" : response.substring(i + 4, Math.min(response.length(), i + 204));
    }

    @Controller("/g11")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Post(value = "/completed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> completed(Publisher<CompletedFileUpload> avatar) {
            return Flux.from(avatar).map(u -> {
                long n = u.getSize();
                return n;
            }).reduce(0L, Long::sum).map(n -> "bytes " + n);
        }

        @Post(value = "/streaming-publisher", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> streamingPublisher(Publisher<StreamingFileUpload> avatar) {
            return Flux.from(avatar).flatMap(Ctrl::count).reduce(0L, Long::sum).map(n -> "bytes " + n);
        }

        @Post(value = "/part-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> partData(Publisher<PartData> avatar) {
            return Flux.from(avatar).map(pd -> (long) pd.getBytes().length).reduce(0L, Long::sum).map(n -> "bytes " + n);
        }

        @Post(value = "/streaming", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> streaming(StreamingFileUpload avatar) {
            return count(avatar).map(n -> "bytes " + n);
        }

        private static Mono<Long> count(StreamingFileUpload upload) {
            long[] n = new long[1];
            java.io.OutputStream counter = new java.io.OutputStream() {
                @Override
                public void write(int b) {
                    n[0]++;
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    n[0] += len;
                }
            };
            return Mono.from(upload.transferTo(counter)).then(Mono.fromSupplier(() -> n[0]));
        }
    }
}
