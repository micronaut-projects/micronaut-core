/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
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
import io.micronaut.http.body.stream.SizeLimitTracker;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.contrib.multipart.ContentDisposition;
import io.netty.contrib.multipart.ParsedHeaderValue;
import io.netty.contrib.multipart.PostBodyDecoder;
import io.netty.contrib.multipart.TooManyFormFieldsException;
import io.netty.contrib.multipart.UndecodedDataLimitExceededException;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This class parses a {@link ByteBody} into a sequence of {@link RawFormField}s.
 *
 * @since 5.0.0
 * @author Jonas Konrad
 */
@Internal
public final class FormDemuxer implements BufferConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(FormDemuxer.class);
    private final PostBodyDecoder decoder;
    private final ByteBodyFactory byteBodyFactory;
    @Nullable
    private final EventLoop eventLoop;
    private final SizeLimitTracker.TrackerPair totalTracker;
    private final BodySizeLimits fieldLimits;
    @Nullable
    private final Upstream upstream;

    private final Sinks.Many<RawFormField> sink = Sinks.many().unicast().onBackpressureBuffer();

    @Nullable
    private State state = new BeforeField(null);

    private boolean fieldsPublisherCancelled = false;
    private long unacknowledged = 0;
    private boolean decodeFailure = false;

    public FormDemuxer(PostBodyDecoder decoder, Channel channel, BodySizeLimits fieldLimits, BodySizeLimits totalLimits, ByteBody byteBody) {
        this.decoder = decoder;
        this.fieldLimits = fieldLimits;
        if (byteBody instanceof AvailableByteBody abb) {
            // NettyBodyAnnotationBinder triggers this branch from outside the EventLoop sometimes,
            // and requires immediate results, without waiting for the EventLoop. That means we
            // need to take some special measures.

            this.eventLoop = null;
            // we need to use a reactive byteBodyFactory here so we don't need to wait for the event loop
            this.byteBodyFactory = new ByteBodyFactory(NettyByteBufferFactory.DEFAULT, NettyReadBufferFactory.of(channel.alloc())) {
            };
            this.totalTracker = SizeLimitTracker.notThreadSafe(totalLimits);
            upstream = null;
            add(abb.toReadBuffer());
            complete();
        } else {
            this.eventLoop = channel.eventLoop();
            this.byteBodyFactory = new NettyByteBodyFactory(channel);
            this.totalTracker = SizeLimitTracker.notThreadSafe(totalLimits).makeBothAtomic();
            try (var s = byteBodyFactory.toStreaming(byteBody)) {
                this.upstream = s.primary(this);
            }
            // we delay streaming until the upstream is set, so let's do that now.
            if (state instanceof OptimisticBufferingContent opt) {
                opt.devolveToStreaming(false);
            }
        }
    }

    public Flux<RawFormField> fields() {
        Flux<RawFormField> flux = sink.asFlux()
            .doOnCancel(this::cancel)
            .doOnDiscard(RawFormField.class, RawFormField::close);
        if (upstream != null) {
            flux = flux.doOnSubscribe(s -> upstream.start());
        }
        return flux;
    }

    private void cancel() {
        if (eventLoop != null && !eventLoop.inEventLoop()) {
            eventLoop.execute(this::cancel);
            return;
        }

        fieldsPublisherCancelled = true;
        updateUpstreamDemand();
    }

    private void handleDecoderException(Exception e) {
        assert eventLoop == null || eventLoop.inEventLoop();
        decodeFailure = true;

        if (e instanceof TooManyFormFieldsException) {
            e = new ContentLengthExceededException("Number of form fields exceeds configured limit");
        } else if (e instanceof UndecodedDataLimitExceededException) {
            e = new ContentLengthExceededException("Length of buffered form field exceeds configured limit");
        }

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
        assert eventLoop == null || eventLoop.inEventLoop();
        while (state != null) {
            PostBodyDecoder.Event event = decoder.next();
            if (event == null) {
                // delay until all available input is processed
                if (upstream != null && state instanceof OptimisticBufferingContent opt) {
                    opt.devolveToStreaming(false);
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

    private void emit(FormFieldMetadata metadata, ByteBody content) {
        assert eventLoop == null || eventLoop.inEventLoop();
        CloseableByteBody moved = content.move();
        Sinks.EmitResult result = sink.tryEmitNext(new RawFormField(metadata, moved));
        if (result.isFailure()) {
            moved.close();
        }
    }

    @Override
    public void add(ReadBuffer rb) {
        assert eventLoop == null || eventLoop.inEventLoop();

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
        assert eventLoop == null || eventLoop.inEventLoop();
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
        assert eventLoop == null || eventLoop.inEventLoop();
        if (state instanceof StreamingContent sc) {
            sc.baseSharedBuffer.discard();
        }
        eof();
    }

    @Override
    public void error(Throwable e) {
        assert eventLoop == null || eventLoop.inEventLoop();
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
            sink.tryEmitComplete();
        }
    }

    private static RuntimeException unexpectedEvent(PostBodyDecoder.Event event) {
        return new IllegalStateException("Unexpected event " + event);
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

        @Nullable
        private ContentDisposition disposition = null;
        @Nullable
        private Long contentLength = null;
        @Nullable
        private MediaType mediaType = null;

        Headers(@Nullable Headers mixedHeaders) {
            this.mixedHeaders = mixedHeaders;
        }

        FormFieldMetadata computeMetadata() {
            FormFieldMetadata fromMixed = mixedHeaders == null ? FormFieldMetadata.EMPTY : mixedHeaders.computeMetadata();
            return new FormFieldMetadata(
                Optional.ofNullable(disposition).map(ContentDisposition::name).orElse(fromMixed.name()),
                disposition == null ? null : disposition.fileName(),
                Optional.ofNullable(mediaType).orElse(fromMixed.mediaType())
            );
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
        private final Headers headers;
        private final List<ByteBuf> buffers = new ArrayList<>(1);
        private final SizeLimitTracker.TrackerPair tracker = SizeLimitTracker.combine(SizeLimitTracker.notThreadSafe(fieldLimits), totalTracker);

        OptimisticBufferingContent(Headers headers) {
            this.headers = headers;
        }

        @Override
        void accept(PostBodyDecoder.Event event) {
            if (event == PostBodyDecoder.Event.CONTENT) {
                ByteBuf content = decoder.decodedContent();
                buffers.add(content);
                if (tracker.add(content.readableBytes()) != null) {
                    this.devolveToStreaming(true);
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
                FormFieldMetadata metadata = headers.computeMetadata();
                buffers.clear(); // avoid double release
                // sizes already checked above
                try (CloseableByteBody body = byteBodyFactory.adapt(((NettyReadBufferFactory) byteBodyFactory.readBufferFactory()).adapt(combined))) {
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

        void devolveToStreaming(boolean fromOverflow) {
            StreamingContent sc = new StreamingContent(headers);
            state = sc;
            try (sc.rootBody) {
                // do this early in case there's an error
                FormFieldMetadata metadata = headers.computeMetadata();

                for (int i = 0; i < buffers.size(); i++) {
                    ByteBuf buffer = buffers.get(i);
                    if (!fromOverflow || i != buffers.size() - 1) {
                        // remove the buffer from the tracker, the SC will track on its own.
                        // on overflow, we didn't add the last buffer to the tracker, so skip that one.
                        tracker.subtract(buffer.readableBytes());
                    }
                    sc.add(buffer);
                }
                buffers.clear();

                if (headers.contentLength != null) {
                    sc.baseSharedBuffer.setExpectedLength(headers.contentLength);
                }
                emit(metadata, sc.rootBody);
            } finally {
                close();
            }
        }
    }

    private final class StreamingContent extends State implements Upstream {
        private final @Nullable Headers mixedHeaders;
        private final BaseSharedBuffer baseSharedBuffer;
        private final BaseStreamingByteBody<?> rootBody;
        private long unacknowledged = 0;
        private boolean cancelled = false;

        StreamingContent(Headers headers) {
            this.mixedHeaders = headers.mixedHeaders;

            ByteBodyFactory.StreamingBody streamingBody = byteBodyFactory.createStreamingBody(fieldLimits, this);
            this.baseSharedBuffer = streamingBody.sharedBuffer();
            this.baseSharedBuffer.addSizeLimitTrackers(totalTracker);
            this.rootBody = streamingBody.rootBody();
        }

        void add(ByteBuf content) {
            unacknowledged += content.readableBytes();
            baseSharedBuffer.add(((NettyReadBufferFactory) byteBodyFactory.readBufferFactory()).adapt(content));
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
            if (eventLoop != null && !eventLoop.inEventLoop()) {
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
            if (eventLoop != null && !eventLoop.inEventLoop()) {
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
