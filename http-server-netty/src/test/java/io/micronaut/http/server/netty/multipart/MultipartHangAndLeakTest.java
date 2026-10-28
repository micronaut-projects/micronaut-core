package io.micronaut.http.server.netty.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.netty.NettyServerCustomizer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.Channel;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Group 01 members that need a running server.
 */
class MultipartHangAndLeakTest {
    private static final String SPEC_NAME = "MultipartHangAndLeakTest";
    static volatile TrackingAllocator alloc = new TrackingAllocator();

    private static EmbeddedServer start(ApplicationContext ctx) {
        return ctx.getBean(EmbeddedServer.class).start();
    }

    /**
     * a multipart @Body with a decode-only charset makes FormDemuxer throw,
     * and the buffered body is never released.
     */
    @Test
    void decodeOnlyCharsetReleasesBody() throws Exception {
        assertReleased("ISO-2022-CN");
    }

    /**
     * Control for the charset: UTF-8 releases everything.
     */
    @Test
    void controlUtf8ReleasesBody() throws Exception {
        assertReleased("UTF-8");
    }

    private void assertReleased(String charset) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = start(ctx);
            TrackingAllocator tracking = new TrackingAllocator();
            alloc = tracking;
            String body = "--xx\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\n" + "n".repeat(256 * 1024) + "\r\n--xx--\r\n";
            String response = RawHttp.exchange(server.getPort(), 5000, RawHttp.withBody(
                "POST /g01/people HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: multipart/form-data; boundary=xx; charset=" + charset + "\r\n",
                body.getBytes(StandardCharsets.ISO_8859_1)));
            assertFalse(response.isEmpty(), "no response");
            long retained = tracking.awaitReleased(3000);
            assertEquals(0, retained, "request buffers still retained after the response [" + response.lines().findFirst().orElse("") + "]");
        }
    }

    /**
     * a part without a name reaches a MultipartBody subscriber as a
     * CompletedPart whose getName() throws, and the route never answers.
     */
    @Test
    void unnamedPartDoesNotHangMultipartBodySubscriber() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = start(ctx);
            String body = "--xyz\r\nContent-Disposition: form-data\r\n\r\nhello\r\n"
                + "--xyz\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.bin\"\r\n\r\n" + "A".repeat(1000) + "\r\n"
                + "--xyz--\r\n";
            String response = RawHttp.exchange(server.getPort(), 5000, RawHttp.withBody(
                "POST /g01/whole-body HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: multipart/form-data; boundary=xyz\r\n",
                body.getBytes(StandardCharsets.ISO_8859_1)));
            assertFalse(response.isEmpty(), "the route never answered a form with an unnamed part");
        }
    }

    /**
     * Control for the unnamed part: the same form with a named first part is answered.
     */
    @Test
    void controlNamedPartIsAnswered() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = start(ctx);
            String body = "--xyz\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\nhello\r\n"
                + "--xyz\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.bin\"\r\n\r\n" + "A".repeat(1000) + "\r\n"
                + "--xyz--\r\n";
            String response = RawHttp.exchange(server.getPort(), 5000, RawHttp.withBody(
                "POST /g01/whole-body HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: multipart/form-data; boundary=xyz\r\n",
                body.getBytes(StandardCharsets.ISO_8859_1)));
            assertFalse(response.isEmpty());
        }
    }

    /**
     * the streamed part arrives first and the route waits for another
     * publisher argument; FormRouteCompleter withholds the already decoded field forever.
     */
    @Test
    void streamedPartFirstDoesNotHang() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = start(ctx);
            String body = "--form-boundary\r\nContent-Disposition: form-data; name=\"avatar\"; filename=\"avatar.png\"\r\n\r\nxxxx\r\n"
                + "--form-boundary\r\nContent-Disposition: form-data; name=\"userId\"\r\n\r\n42\r\n"
                + "--form-boundary--\r\n";
            String response = RawHttp.exchange(server.getPort(), 5000, RawHttp.withBody(
                "POST /g01/avatar HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Type: multipart/form-data; boundary=form-boundary\r\n",
                body.getBytes(StandardCharsets.ISO_8859_1)));
            assertFalse(response.isEmpty(), "the route never answered a form whose streamed part came first");
        }
    }

    /**
     * A close delimiter line in the header block of the last part, with no real close delimiter
     * after it, must fail the body rather than leave the streamed part open.
     */
    @Test
    void closeDelimiterInsidePartHeadersIsRejected() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = start(ctx);
            String part1 = "--b\r\n"
                + "Content-Disposition: form-data; name=\"a\"\r\n\r\n"
                + "1\r\n"
                + "--b\r\n"
                + "--b--\r\n"
                + "Content-Disposition: form-data; name=\"f\"; filename=\"f.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + "da";
            String part2 = "ta-without-a-close-delimiter";
            String head = "POST /g01/upload HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                + "Content-Type: multipart/form-data; boundary=b\r\nContent-Length: " + (part1.length() + part2.length()) + "\r\n\r\n";
            String response = RawHttp.exchange(server.getPort(), 5000, RawHttp.ascii(head + part1), RawHttp.ascii(part2));
            assertTrue(response.startsWith("HTTP/1.1 400"), "not answered with 400: [" + response + "]");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AllocatorCustomizer implements BeanCreatedEventListener<NettyServerCustomizer.Registry>, NettyServerCustomizer {
        @Override
        public NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.getBean().register(this);
            return event.getBean();
        }

        @Override
        public NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            if (role == ChannelRole.CONNECTION) {
                channel.config().setAllocator(alloc);
            }
            return this;
        }
    }

    @Controller("/g01")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Post(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String upload(String a, CompletedFileUpload f) throws Exception {
            return "upload a=" + a + " f=" + f.getFilename() + " bytes=" + f.getBytes().length;
        }

        @Post(value = "/people", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String people(@Body Map<String, Object> form) {
            return "ok " + form.keySet();
        }

        // the guide's "Whole Body Binding" example, with the request(1) it lacks
        @Post(value = "/whole-body", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Publisher<HttpResponse<String>> wholeBody(@Body MultipartBody body) {
            return Mono.create(emitter -> Flux.from(body).publishOn(Schedulers.boundedElastic()).subscribe(new Subscriber<CompletedPart>() {
                private Subscription s;

                @Override
                public void onSubscribe(Subscription s) {
                    this.s = s;
                    s.request(1);
                }

                @Override
                public void onNext(CompletedPart completedPart) {
                    String partName = completedPart.getName();
                    if (partName == null) {
                        throw new IllegalStateException();
                    }
                    s.request(1);
                }

                @Override
                public void onError(Throwable t) {
                    emitter.error(t);
                }

                @Override
                public void onComplete() {
                    emitter.success(HttpResponse.ok("Uploaded"));
                }
            }));
        }

        @Post(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        Mono<String> avatar(Publisher<Integer> userId, Publisher<StreamingFileUpload> avatar) {
            return Mono.from(userId).flatMap(id -> Flux.from(avatar)
                .flatMap(upload -> Mono.from(upload.transferTo(java.io.OutputStream.nullOutputStream())).thenReturn(1))
                .reduce(0, Integer::sum)
                .map(n -> "user " + id + " uploads " + n));
        }
    }
}
