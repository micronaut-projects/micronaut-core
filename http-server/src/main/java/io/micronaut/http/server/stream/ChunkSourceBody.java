/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.ChunkSource;
import io.micronaut.http.body.CloseableByteBody;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pulls the elements of a {@link ChunkSource} into a {@link BodyStream}: one {@link ChunkSource#next()}
 * at a time, and only while the stream is writable.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ChunkSourceBody {

    private static final Logger LOG = LoggerFactory.getLogger(ChunkSourceBody.class);

    private static final int PENDING = 0;
    private static final int COMPLETED_IN_LOOP = 1;
    private static final int LEFT_LOOP = 2;

    private final ChunkSource<?> source;
    private final ResponseStreams.ElementEncoder encoder;
    /**
     * Whether the elements are framed as a JSON array, decided on the first element.
     */
    private boolean jsonArray;
    private final BodyStream stream;
    private final ByteBodyFactory factory;
    private final PropagatedContext context;
    private final DelayedExecutionFlow<CloseableByteBody> firstElement = DelayedExecutionFlow.create();
    /**
     * Whether a pull loop runs or a {@link ChunkSource#next()} is pending.
     */
    private final AtomicBoolean pulling = new AtomicBoolean();
    private final AtomicBoolean sourceClosed = new AtomicBoolean();
    /**
     * Only changed by the owner of {@link #pulling}.
     */
    private boolean first = true;

    private ChunkSourceBody(ByteBodyFactory factory, ChunkSource<?> source, ResponseStreams.ElementEncoder encoder) {
        this.factory = factory;
        this.source = source;
        this.encoder = encoder;
        this.context = PropagatedContext.getOrEmpty();
        this.stream = new BodyStream(factory, context, ResponseStreams.DEFAULT_HIGH_WATER_MARK, false);
    }

    /**
     * Start pulling the source.
     *
     * @param factory   The body factory of the response
     * @param source    The source
     * @param encoder   Encodes the elements
     * @return Completes with the body once the first element or the end is available, or
     * exceptionally if the first element fails
     */
    static ExecutionFlow<CloseableByteBody> start(ByteBodyFactory factory, ChunkSource<?> source, ResponseStreams.ElementEncoder encoder) {
        ChunkSourceBody body = new ChunkSourceBody(factory, source, encoder);
        body.stream.onClose(ignored -> body.closeSource());
        body.stream.onDemand(body::pull);
        body.pull();
        return body.firstElement;
    }

    private void pull() {
        if (pulling.compareAndSet(false, true)) {
            loop();
        }
    }

    /**
     * Pull while the stream is writable. Runs while owning {@link #pulling}.
     */
    private void loop() {
        while (true) {
            if (!stream.isOpen() || (!first && !stream.isWritable())) {
                pulling.set(false);
                // the stream may have become writable after the check: its demand did not pull,
                // since this loop still owned the pull
                if (stream.isOpen() && stream.isWritable() && pulling.compareAndSet(false, true)) {
                    continue;
                }
                return;
            }
            CompletionStage<? extends Optional<?>> next;
            try {
                next = source.next();
                if (next == null) {
                    throw new NullPointerException("The chunk source returned no stage");
                }
            } catch (Throwable e) {
                failed(e);
                return;
            }
            AtomicInteger iteration = new AtomicInteger(PENDING);
            next.whenComplete((element, error) -> {
                if (!onNext(element, error)) {
                    return;
                }
                if (!iteration.compareAndSet(PENDING, COMPLETED_IN_LOOP)) {
                    // completed later, on another thread: continue there
                    context.propagate(this::loop);
                }
            });
            if (!iteration.compareAndSet(PENDING, LEFT_LOOP)) {
                // completed synchronously: continue in this loop instead of recursing
                continue;
            }
            return;
        }
    }

    /**
     * @return Whether to continue pulling
     */
    private boolean onNext(@Nullable Optional<?> element, @Nullable Throwable error) {
        if (error != null) {
            failed(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            return false;
        }
        if (element == null || element.isEmpty()) {
            if (first ? encoder.jsonArray(null) : jsonArray) {
                stream.write(factory.readBufferFactory().copyOf(first ? "[]" : "]", StandardCharsets.UTF_8));
            }
            stream.complete();
            respond();
            return false;
        }
        ReadBuffer data;
        try {
            if (first) {
                jsonArray = encoder.jsonArray(element.get());
            }
            byte @Nullable [] prefix = !jsonArray ? null : first ? JsonFraming.OPEN : JsonFraming.COMMA;
            data = encoder.encode(element.get(), prefix);
        } catch (Throwable e) {
            failed(e);
            return false;
        }
        stream.write(data);
        respond();
        if (!stream.isOpen()) {
            // the client left
            pulling.set(false);
            return false;
        }
        return true;
    }

    private void respond() {
        if (first) {
            first = false;
            firstElement.complete(stream.body());
        }
    }

    private void failed(Throwable error) {
        if (first) {
            // nothing was sent: the error is answered like an error of the route
            first = false;
            stream.abandon(error);
            firstElement.completeExceptionally(error);
        } else {
            stream.fail(error);
        }
    }

    private void closeSource() {
        if (sourceClosed.compareAndSet(false, true)) {
            try {
                source.close();
            } catch (Throwable e) {
                LOG.warn("Failed to close the chunk source of a response", e);
            }
        }
    }

    /**
     * The separators of the elements of a JSON array.
     */
    private static final class JsonFraming {
        static final byte[] OPEN = "[".getBytes(StandardCharsets.UTF_8);
        static final byte[] COMMA = ",".getBytes(StandardCharsets.UTF_8);
    }
}
