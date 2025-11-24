package io.micronaut.http.server.netty.multipart;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.form.FormFieldMetadata;
import io.micronaut.http.form.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.contrib.multipart.ContentDisposition;
import io.netty.contrib.multipart.ParsedHeaderValue;
import io.netty.contrib.multipart.PostBodyDecoder;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

/**
 * This class parses a {@link ByteBody} into a sequence of form {@link Field}s.
 */
@Internal
public final class FormDemuxer implements BufferConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(FormDemuxer.class);
    private final PostBodyDecoder decoder;
    private final NettyByteBodyFactory byteBodyFactory;
    private final EventLoop eventLoop;
    private final BodySizeLimits fieldLimits;
    @Nullable
    private final Upstream upstream;

    private final Sinks.Many<RawFormField> sink = Sinks.many().unicast().onBackpressureBuffer();

    private State state = new BeforeField(null);

    private boolean fieldsPublisherCancelled = false;
    private long unacknowledged = 0;
    private boolean decodeFailure = false;

    public FormDemuxer(PostBodyDecoder decoder, Channel channel, BodySizeLimits fieldLimits, ByteBody byteBody) {
        this.decoder = decoder;
        this.byteBodyFactory = new NettyByteBodyFactory(channel);
        this.eventLoop = channel.eventLoop();
        this.fieldLimits = fieldLimits;
        if (byteBody instanceof AvailableByteBody abb) {
            upstream = null;
            add(abb.toReadBuffer());
            complete();
        } else {
            try (StreamingNettyByteBody s = byteBodyFactory.toStreaming(byteBody)) {
                this.upstream = s.primary(this);
            }
            // we delay streaming until the upstream is set, so let's do that now.
            if (state instanceof OptimisticBufferingContent opt) {
                opt.devolveToStreaming();
            }
        }
    }

    public Flux<RawFormField> fields() {
        return sink.asFlux()
            .doOnCancel(this::cancel)
            .doOnDiscard(Field.class, f -> f.body.close());
    }

    private void cancel() {
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(this::cancel);
            return;
        }

        fieldsPublisherCancelled = true;
        updateUpstreamDemand();
    }

    private void handleDecoderException(Exception e) {
        assert eventLoop.inEventLoop();
        decodeFailure = true;
        if (state instanceof StreamingContent sc) {
            sc.baseSharedBuffer.error(e);
        }
        if (sink.tryEmitError(e).isFailure()) {
            LOG.debug("Failed to forward decoder failure", e);
        }
        updateUpstreamDemand();
        eof();
    }

    private void forwardOutput() {
        assert eventLoop.inEventLoop();
        while (state != null) {
            PostBodyDecoder.Event event = decoder.next();
            if (event == null) {
                // delay until all available input is processed
                if (upstream != null && state instanceof OptimisticBufferingContent opt) {
                    opt.devolveToStreaming();
                }
                break;
            } else {
                state.accept(event);
            }
        }
    }

    private void updateUpstreamDemand() {
        if (upstream == null) {
            return;
        }

        if (decodeFailure || state == null) {
            upstream.allowDiscard();
            return;
        }

        if (state instanceof StreamingContent sc) {
            if (fieldsPublisherCancelled && sc.cancelled) {
                eof();
                upstream.allowDiscard();
                return;
            }
            // check whether there's backpressure from the streaming state.
            if (!sc.hasDemand()) {
                return;
            }
        } else {
            if (fieldsPublisherCancelled) {
                eof();
                upstream.allowDiscard();
                return;
            }
            // non-streaming states have infinite demand.
        }

        long unacknowledged = this.unacknowledged;
        if (unacknowledged > 0) {
            this.unacknowledged = 0;
            upstream.onBytesConsumed(unacknowledged);
        }
    }

    private void emit(FieldMetadata metadata, ByteBody content) {
        assert eventLoop.inEventLoop();
        CloseableByteBody moved = content.move();
        Sinks.EmitResult result = sink.tryEmitNext(new RawFormField(new FormFieldMetadata(metadata.name, metadata.filename, metadata.mediaType), moved));
        if (result.isFailure()) {
            moved.close();
        }
    }

    @Override
    public void add(@NonNull ReadBuffer rb) {
        assert eventLoop.inEventLoop();

        if (state == null) {
            rb.close();
            return;
        }

        unacknowledged += rb.readable();
        try {
            decoder.add(NettyReadBufferFactory.toByteBuf(rb));
            forwardOutput();
        } catch (Exception e) {
            handleDecoderException(e);
            return;
        }
        updateUpstreamDemand();
    }

    @Override
    public void complete() {
        assert eventLoop.inEventLoop();
        try {
            decoder.endInput();
        } catch (Exception e) {
            handleDecoderException(e);
            return;
        }
        forwardOutput();
        eof();
    }

    @Override
    public void discard() {
        assert eventLoop.inEventLoop();
        if (state instanceof StreamingContent sc) {
            sc.baseSharedBuffer.discard();
        }
        eof();
    }

    @Override
    public void error(Throwable e) {
        assert eventLoop.inEventLoop();
        if (state instanceof StreamingContent sc) {
            sc.baseSharedBuffer.error(e);
        }
        if (sink.tryEmitError(e).isFailure()) {
            LOG.debug("Failed to forward failure", e);
        }
        eof();
    }

    private void eof() {
        if (state != null) {
            if (state instanceof OptimisticBufferingContent opt) {
                opt.close();
            }
            state = null;
            decoder.close();
        }
    }

    private static @NonNull RuntimeException unexpectedEvent(PostBodyDecoder.Event event) {
        return new IllegalStateException("Unexpected event " + event);
    }

    public record Field( // TODO: replace
        FieldMetadata metadata,
        CloseableByteBody body
    ) {
    }

    public record FieldMetadata( // TODO: replace
        @Nullable String name,
        @Nullable String filename,
        @Nullable Long length,
        @Nullable MediaType mediaType
    ) {
        FieldMetadata inheritFromMixed(FieldMetadata mixedMetadata) {
            // we inherit the name and media type, but not the filename or length.
            return new FieldMetadata(
                this.name == null ? mixedMetadata.name : this.name,
                this.filename,
                this.length,
                this.mediaType == null ? mixedMetadata.mediaType : this.mediaType
            );
        }
    }

    private abstract static sealed class State {
        abstract void accept(PostBodyDecoder.Event event);
    }

    private final class BeforeField extends State {
        private final @Nullable Headers mixedHeaders;

        BeforeField(@Nullable Headers mixedHeaders) {
            this.mixedHeaders = mixedHeaders;
        }

        @Override
        void accept(PostBodyDecoder.Event event) {
            if (event == PostBodyDecoder.Event.BEGIN_FIELD) {
                state = new Headers(mixedHeaders);
            } else if (event == PostBodyDecoder.Event.FIELD_COMPLETE && mixedHeaders != null) {
                state = new BeforeField(null);
            } else {
                throw unexpectedEvent(event);
            }
        }
    }

    private final class Headers extends State {
        private final @Nullable Headers mixedHeaders;

        private ContentDisposition disposition = null;
        private Long contentLength = null;
        private MediaType mediaType = null;

        Headers(@Nullable Headers mixedHeaders) {
            this.mixedHeaders = mixedHeaders;
        }

        FieldMetadata computeMetadata() {
            FieldMetadata computed = new FieldMetadata(
                disposition == null ? null : disposition.name(),
                disposition == null ? null : disposition.fileName(),
                contentLength,
                mediaType
            );
            if (mixedHeaders != null) {
                computed = computed.inheritFromMixed(mixedHeaders.computeMetadata());
            }
            return computed;
        }

        @Override
        void accept(PostBodyDecoder.Event event) {
            if (event == PostBodyDecoder.Event.HEADER) {
                ParsedHeaderValue parsedHeaderValue = decoder.parsedHeaderValue();
                if (parsedHeaderValue instanceof ContentDisposition cd) {
                    this.disposition = cd;
                } else if (HttpHeaderNames.CONTENT_TYPE.contentEquals(decoder.headerName())) {
                    this.mediaType = MediaType.of(decoder.headerValue());
                } else if (HttpHeaderNames.CONTENT_LENGTH.contentEquals(decoder.headerName())) {
                    this.contentLength = Long.parseLong(decoder.headerValue());
                }
            } else if (event == PostBodyDecoder.Event.HEADERS_COMPLETE) {
                state = new OptimisticBufferingContent(this);
            } else if (event == PostBodyDecoder.Event.BEGIN_MIXED && mixedHeaders == null) {
                state = new Headers(this);
            } else {
                throw unexpectedEvent(event);
            }
        }
    }

    private final class OptimisticBufferingContent extends State {
        private final @NonNull Headers headers;
        private final List<ByteBuf> buffers = new ArrayList<>(1);
        private long accumulated;

        OptimisticBufferingContent(@NonNull Headers headers) {
            this.headers = headers;
        }

        @Override
        void accept(PostBodyDecoder.Event event) {
            if (event == PostBodyDecoder.Event.CONTENT) {
                ByteBuf content = decoder.decodedContent();
                accumulated = accumulated + content.readableBytes();
                buffers.add(content);
                if (accumulated > fieldLimits.maxBufferSize() || accumulated > fieldLimits.maxBodySize()) {
                    this.devolveToStreaming();
                }
            } else if (event == PostBodyDecoder.Event.FIELD_COMPLETE) {
                ByteBuf combined;
                if (buffers.isEmpty()) {
                    combined = Unpooled.EMPTY_BUFFER;
                } else if (buffers.size() == 1) {
                    combined = buffers.getFirst();
                } else {
                    CompositeByteBuf composite = buffers.getFirst().alloc().compositeBuffer(buffers.size());
                    for (ByteBuf buffer : buffers) {
                        composite.addComponent(true, buffer);
                    }
                    combined = composite;
                }
                FieldMetadata metadata = headers.computeMetadata();
                try (CloseableByteBody body = byteBodyFactory.createChecked(fieldLimits, combined)) {
                    emit(metadata, body);
                }

                state = new BeforeField(headers.mixedHeaders);
            } else {
                throw unexpectedEvent(event);
            }
        }

        void close() {
            for (ByteBuf buffer : buffers) {
                buffer.release();
            }
            buffers.clear();
        }

        void devolveToStreaming() {
            StreamingContent sc = new StreamingContent(headers);
            state = sc;
            try {
                for (ByteBuf buffer : buffers) {
                    sc.add(buffer);
                }
                buffers.clear();
            } finally {
                close();
            }
        }
    }

    private final class StreamingContent extends State implements Upstream {
        private final @Nullable Headers mixedHeaders;
        private final BaseSharedBuffer baseSharedBuffer;
        private long unacknowledged = 0;
        private boolean cancelled = false;

        StreamingContent(Headers headers) {
            this.mixedHeaders = headers.mixedHeaders;

            // do this early in case there's an error
            FieldMetadata metadata = headers.computeMetadata();

            ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(fieldLimits, this);
            try (BaseStreamingByteBody<?> rb = streamingBody.rootBody()) {
                this.baseSharedBuffer = streamingBody.sharedBuffer();
                if (metadata.length != null) {
                    baseSharedBuffer.setExpectedLength(metadata.length);
                }
                emit(metadata, rb);
            }
        }

        void add(ByteBuf content) {
            unacknowledged += content.readableBytes();
            baseSharedBuffer.add(byteBodyFactory.readBufferFactory().adapt(content));
        }

        @Override
        void accept(PostBodyDecoder.Event event) {
            if (event == PostBodyDecoder.Event.CONTENT) {
                add(decoder.decodedContent());
            } else if (event == PostBodyDecoder.Event.FIELD_COMPLETE) {
                baseSharedBuffer.complete();
                if (fieldsPublisherCancelled) {
                    eof();
                } else {
                    state = new BeforeField(mixedHeaders);
                }
            } else {
                throw unexpectedEvent(event);
            }
        }

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(() -> onBytesConsumed(bytesConsumed));
                return;
            }
            long newUnacknowledged = unacknowledged - bytesConsumed;
            if (newUnacknowledged > unacknowledged) {
                // guard against underflow
                unacknowledged = Long.MIN_VALUE;
            } else {
                unacknowledged = newUnacknowledged;
            }
            updateUpstreamDemand();
        }

        @Override
        public void allowDiscard() {
            if (state != this) {
                // shortcut
                return;
            }
            if (!eventLoop.inEventLoop()) {
                eventLoop.execute(this::allowDiscard);
                return;
            }
            unacknowledged = Long.MIN_VALUE;
            cancelled = true;
            updateUpstreamDemand();
        }

        boolean hasDemand() {
            return unacknowledged <= 0;
        }
    }
}
