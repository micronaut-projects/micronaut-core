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
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseElements;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls the elements of a {@link ResponseElements} body into a {@link BodyStream}: one
 * {@link ResponseElements#next()} at a time, and only while the stream is writable.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ResponseElementsBody {

    private final ResponseElements<?> elements;
    private final ResponseStreams.ElementEncoder encoder;
    private final BodyStream stream;
    private final ByteBodyFactory factory;
    private final DelayedExecutionFlow<CloseableByteBody> firstElement = DelayedExecutionFlow.create();
    /**
     * Whether a pull loop runs or a {@link ResponseElements#next()} is pending.
     */
    private final AtomicBoolean pulling = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    /**
     * The framing of the elements, decided on the first element. Only changed by the owner of
     * {@link #pulling}, like {@link #first}.
     */
    private ResponseStreams.Framing framing = ResponseStreams.Framing.NONE;
    private boolean first = true;
    private final PropagatedContext context;

    private ResponseElementsBody(ByteBodyFactory factory, ResponseElements<?> elements, ResponseStreams.ElementEncoder encoder, int highWaterMark) {
        this.factory = factory;
        this.elements = elements;
        this.encoder = encoder;
        this.context = PropagatedContext.getOrEmpty();
        this.stream = new BodyStream(factory, context, highWaterMark, false);
    }

    /**
     * Start pulling the elements.
     *
     * @param factory       The body factory of the response
     * @param elements      The elements
     * @param encoder       Encodes the elements
     * @param highWaterMark The high-water mark of the stream
     * @return Completes with the body once the first element or the end is available, or
     * exceptionally if the first element fails
     */
    static ExecutionFlow<CloseableByteBody> start(ByteBodyFactory factory, ResponseElements<?> elements, ResponseStreams.ElementEncoder encoder, int highWaterMark) {
        ResponseElementsBody body = new ResponseElementsBody(factory, elements, encoder, highWaterMark);
        body.stream.onClose(ignored -> body.close());
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
                next = Objects.requireNonNull(elements.next(), "The response elements returned no stage");
            } catch (Throwable e) {
                failed(e);
                return;
            }
            // the second of the stage and this loop to get here continues the pull: this loop if
            // the stage completed synchronously, instead of recursing, else the completing thread
            AtomicBoolean handOff = new AtomicBoolean();
            next.whenComplete((element, error) -> {
                if (onNext(element, error) && handOff.getAndSet(true)) {
                    context.propagate(this::loop);
                }
            });
            if (!handOff.getAndSet(true)) {
                return;
            }
        }
    }

    /**
     * @return Whether to continue pulling
     */
    private boolean onNext(@Nullable Object result, @Nullable Throwable error) {
        if (error != null) {
            failed(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            return false;
        }
        if (!(result instanceof Optional<?> element) || element.isEmpty()) {
            if (first) {
                framing = encoder.framing(null);
            }
            ReadBuffer end = framing.end(factory.readBufferFactory(), first);
            if (end != null) {
                stream.write(end);
            }
            stream.complete();
            respond();
            return false;
        }
        ReadBuffer data;
        try {
            if (first) {
                framing = encoder.framing(element.get());
            }
            data = encoder.encode(element.get(), framing, first);
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

    private void close() {
        if (closed.compareAndSet(false, true)) {
            ResponseStreams.discard(elements);
        }
    }
}
