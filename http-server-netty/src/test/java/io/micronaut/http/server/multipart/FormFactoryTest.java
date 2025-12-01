package io.micronaut.http.server.multipart;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.form.FormCapableHttpRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ParameterizedClass
@ValueSource(booleans = {true, false})
@Timeout(20)
class FormFactoryTest {
    private final List<Runnable> disposeTasks = new ArrayList<>();
    private final FormCapableHttpRequest<?> mockRequest;

    FormFactoryTest(boolean netty) {
        if (netty) {
            mockRequest = new NettyHttpRequest<>(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"),
                AvailableByteArrayBody.create(ReadBufferFactory.getJdkFactory().createEmpty()),
                new EmbeddedChannel().pipeline().addLast(new ChannelInboundHandlerAdapter()).firstContext(),
                ConversionService.SHARED,
                new HttpServerConfiguration()
            );
            disposeTasks.add(((NettyHttpRequest<?>) mockRequest)::release);
        } else {
            mockRequest = new MockNonNettyRequest();
        }
    }

    @AfterEach
    void tearDown() {
        disposeTasks.forEach(Runnable::run);
        disposeTasks.clear();
    }

    @ParameterizedTest
    @EnumSource
    public void completedSimple(StorageMode mode) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(mode.config)) {
            var factory = ctx.getBean(FormFactory.class);
            FormFieldMetadata metadata = new FormFieldMetadata("foo", "bar.txt", MediaType.TEXT_PLAIN_TYPE);
            try (var upload = factory.completeFileUpload(mockRequest, new RawFormField(
                metadata,
                mockRequest.byteBodyFactory().copyOf("fizzbuzz", UTF_8)
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
            ByteBodyFactory.StreamingBody streamingBody = mockRequest.byteBodyFactory().createStreamingBody(BodySizeLimits.UNLIMITED, new MockUpstream());
            ExecutionFlow<CompletedFileUpload> flow = factory.completeFileUpload(mockRequest, new RawFormField(
                metadata,
                streamingBody.rootBody()
            ));
            streamingBody.sharedBuffer().add(mockRequest.byteBodyFactory().readBufferFactory().copyOf("fizz", UTF_8));
            streamingBody.sharedBuffer().add(mockRequest.byteBodyFactory().readBufferFactory().copyOf("buzz", UTF_8));
            streamingBody.sharedBuffer().complete();
            try (var upload = flow.toCompletableFuture().get()) {
                assertEquals(metadata, upload.getMetadata());
                assertEquals("fizzbuzz", new String(upload.getBytes(), UTF_8));
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

    private class MockNonNettyRequest implements FormCapableHttpRequest<Object> {
        @Override
        public @NonNull Publisher<RawFormField> getRawFormFields() throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasFormBody() {
            return true;
        }

        @Override
        public void addDisposalResource(@NonNull Runnable dispose) {
            disposeTasks.add(dispose);
        }

        @Override
        public @NonNull ByteBody byteBody() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull Cookies getCookies() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull HttpParameters getParameters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.micronaut.http.@NonNull HttpMethod getMethod() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull URI getUri() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull HttpHeaders getHeaders() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull MutableConvertibleValues<Object> getAttributes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull Optional<Object> getBody() {
            throw new UnsupportedOperationException();
        }
    }
}
