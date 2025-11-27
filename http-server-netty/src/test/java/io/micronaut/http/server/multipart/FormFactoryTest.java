package io.micronaut.http.server.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ParameterizedClass
@ValueSource(booleans = {true, false})
@Timeout(20)
class FormFactoryTest {
    private final HttpRequest<?> mockRequest;

    FormFactoryTest(boolean netty) {
        if (netty) {
            mockRequest = new NettyHttpRequest<>(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"),
                null,
                new EmbeddedChannel().pipeline().addLast(new ChannelInboundHandlerAdapter()).firstContext(),
                ConversionService.SHARED,
                new HttpServerConfiguration()
            );
        } else {
            mockRequest = null;
        }
    }

    private ByteBodyFactory bodyFactory() {
        return FormFactory.bodyFactory(mockRequest);
    }

    @ParameterizedTest
    @EnumSource
    public void completedSimple(StorageMode mode) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(mode.config)) {
            var factory = ctx.getBean(FormFactory.class);
            FormFieldMetadata metadata = new FormFieldMetadata("foo", "bar.txt", MediaType.TEXT_PLAIN_TYPE);
            try (var upload = factory.completeFileUpload(mockRequest, new RawFormField(
                metadata,
                bodyFactory().copyOf("fizzbuzz", UTF_8)
            )).toCompletableFuture().get()) {
                assertEquals(metadata, upload.getMetadata());
                assertEquals("fizzbuzz", new String(upload.getBytes(), UTF_8));
            }
        }
    }

    @ParameterizedTest
    @EnumSource
    public void completedMulti(StorageMode mode) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(mode.config)) {
            var factory = ctx.getBean(FormFactory.class);
            FormFieldMetadata metadata = new FormFieldMetadata("foo", "bar.txt", MediaType.TEXT_PLAIN_TYPE);
            ByteBodyFactory.StreamingBody streamingBody = bodyFactory().createStreamingBody(BodySizeLimits.UNLIMITED, new MockUpstream());
            ExecutionFlow<CompletedFileUpload> flow = factory.completeFileUpload(mockRequest, new RawFormField(
                metadata,
                streamingBody.rootBody()
            ));
            streamingBody.sharedBuffer().add(bodyFactory().readBufferFactory().copyOf("fizz", UTF_8));
            streamingBody.sharedBuffer().add(bodyFactory().readBufferFactory().copyOf("buzz", UTF_8));
            streamingBody.sharedBuffer().complete();
            try (var upload = flow.toCompletableFuture().get()) {
                assertEquals(metadata, upload.getMetadata());
                assertEquals("fizzbuzz", new String(upload.getBytes(), UTF_8));
            }
        }
    }

    @ParameterizedTest
    @EnumSource
    public void streamingLive(StorageMode mode) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(mode.config)) {
            var factory = ctx.getBean(FormFactory.class);
            FormFieldMetadata metadata = new FormFieldMetadata("foo", "bar.txt", MediaType.TEXT_PLAIN_TYPE);
            ByteBodyFactory.StreamingBody streamingBody = bodyFactory().createStreamingBody(BodySizeLimits.UNLIMITED, new MockUpstream());
            try (var upload = factory.streamFileUpload(mockRequest, new RawFormField(
                metadata,
                streamingBody.rootBody()
            )); var live = upload.streamingBody()) {
                streamingBody.sharedBuffer().add(bodyFactory().readBufferFactory().copyOf("foo", UTF_8));
                var sub = new BlockingSubscriber<ReadBuffer>();
                live.toReadBufferPublisher().subscribe(sub);

                assertEquals("foo", sub.next().toString(UTF_8));

                streamingBody.sharedBuffer().add(bodyFactory().readBufferFactory().copyOf("bar", UTF_8));
                assertEquals("bar", sub.next().toString(UTF_8));

                streamingBody.sharedBuffer().complete();
                sub.expectComplete();
            }
        }
    }

    private static final class MockUpstream implements BufferConsumer.Upstream {
        boolean allowDiscard = false;
        boolean disregardBackpressure = false;
        long consumed = 0;

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            this.consumed = Math.addExact(bytesConsumed, consumed);
        }

        @Override
        public void allowDiscard() {
            allowDiscard = true;
        }

        @Override
        public void disregardBackpressure() {
            disregardBackpressure = true;
        }
    }

    enum StorageMode {
        STANDARD(Map.of()),
        MIXED(Map.of("micronaut.server.multipart.mixed", true, "micronaut.server.multipart.threshold", 4)),
        DISK(Map.of("micronaut.server.multipart.disk", true));

        final Map<String, Object> config;

        StorageMode(Map<String, Object> config) {
            this.config = config;
        }
    }
}
