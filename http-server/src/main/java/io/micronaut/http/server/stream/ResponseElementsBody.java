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
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ResponseElements;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls the elements of a {@link ResponseElements} body into a {@link BodyStream}: one
 * {@link ResponseElements#next()} at a time, and only while the stream is writable. An element
 * is encoded, possibly on another thread, and written before the next one is pulled.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ResponseElementsBody {

    private final ResponseElements<?> elements;
    private final ResponseStreams.ElementEncoder encoder;
    private final BodyStream stream;
    private final PropagatedContext context;
    private final DelayedExecutionFlow<CloseableByteBody> firstElement = DelayedExecutionFlow.create();
    /**
     * Whether a pull loop runs or an element is on its way.
     */
    private final AtomicBoolean pulling = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    /**
     * Only changed by the owner of {@link #pulling}.
     */
    private boolean first = true;

    private ResponseElementsBody(ByteBodyFactory factory, ResponseElements<?> elements, ResponseStreams.ElementEncoder encoder, int highWaterMark) {
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
            // the second of the step and this loop to get here continues the pull: this loop if
            // the step completed synchronously, instead of recursing, else the completing thread
            AtomicBoolean handOff = new AtomicBoolean();
            step(proceed -> {
                if (proceed && handOff.getAndSet(true)) {
                    context.propagate(this::loop);
                }
            });
            if (!handOff.getAndSet(true)) {
                return;
            }
        }
    }

    /**
     * Pull, encode and write one element, or end the body.
     *
     * @param done Called once the element is written, with whether to continue pulling
     */
    private void step(StepDone done) {
        CompletionStage<? extends Optional<?>> next;
        try {
            next = Objects.requireNonNull(elements.next(), "The response elements returned no stage");
        } catch (Throwable e) {
            failed(e);
            done.accept(false);
            return;
        }
        next.whenComplete((result, error) -> {
            if (error != null) {
                failed(unwrap(error));
                done.accept(false);
            } else if (!(result instanceof Optional<?> element) || element.isEmpty()) {
                end();
                done.accept(false);
            } else {
                encode(element.get(), done);
            }
        });
    }

    private void encode(Object element, StepDone done) {
        ExecutionFlow<CloseableByteBody> encoded;
        try {
            encoded = encoder.encode(element);
        } catch (Throwable e) {
            failed(e);
            done.accept(false);
            return;
        }
        encoded.onComplete((piece, error) -> {
            if (error != null || piece == null) {
                failed(error == null ? new NullPointerException("The element was encoded to no body") : unwrap(error));
                done.accept(false);
                return;
            }
            if (piece instanceof CloseableAvailableByteBody available) {
                write(available, done);
            } else {
                piece.buffer().whenComplete((available, bufferError) -> {
                    if (bufferError != null) {
                        failed(unwrap(bufferError));
                        done.accept(false);
                    } else {
                        write(available, done);
                    }
                });
            }
        });
    }

    private void write(CloseableAvailableByteBody piece, StepDone done) {
        stream.write(piece.toReadBuffer());
        respond();
        if (!stream.isOpen()) {
            // the client left
            pulling.set(false);
            done.accept(false);
        } else {
            done.accept(true);
        }
    }

    private void end() {
        ReadBuffer end = encoder.end(first);
        if (end != null) {
            stream.write(end);
        }
        stream.complete();
        respond();
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
            try {
                encoder.close();
            } finally {
                ResponseStreams.discard(elements);
            }
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /**
     * Called once when a step finished.
     */
    @FunctionalInterface
    private interface StepDone {
        /**
         * @param proceed Whether to continue pulling
         */
        void accept(boolean proceed);
    }
}
