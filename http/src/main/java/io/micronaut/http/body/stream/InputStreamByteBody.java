/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalLong;
import java.util.concurrent.Executor;

/**
 * {@link io.micronaut.http.body.ByteBody} implementation that reads from an InputStream.
 *
 * @since 4.6.0
 * @author Jonas Konrad
 */
@Experimental
public final class InputStreamByteBody extends InternalByteBody implements CloseableByteBody {
    // originally from micronaut-servlet

    private final Context context;
    private @Nullable ExtendedInputStream stream;

    private InputStreamByteBody(Context context, ExtendedInputStream stream) {
        this.context = context;
        this.stream = stream;
    }

    /**
     * Create a new stream-based {@link CloseableByteBody}. Ownership of the stream is transferred
     * to the returned body.
     *
     * @param stream The stream backing the body
     * @param length The expected content length (see {@link #expectedLength()})
     * @param ioExecutor An executor where blocking {@link InputStream#read()} may be performed
     * @param bufferFactory A {@link ByteBufferFactory} for buffer-based methods
     * @return The body
     * @deprecated Please pass a {@link ByteBodyFactory} instead
     * ({@link #create(InputStream, OptionalLong, Executor, ByteBodyFactory)})
     */
    public static CloseableByteBody create(InputStream stream, OptionalLong length, Executor ioExecutor, ByteBufferFactory<?, ?> bufferFactory) {
        ArgumentUtils.requireNonNull("bufferFactory", bufferFactory);
        return create(stream, length, ioExecutor, ByteBodyFactory.createDefault(bufferFactory));
    }

    /**
     * Create a new stream-based {@link CloseableByteBody}. Ownership of the stream is transferred
     * to the returned body.
     *
     * @param stream The stream backing the body
     * @param length The expected content length (see {@link #expectedLength()})
     * @param ioExecutor An executor where blocking {@link InputStream#read()} may be performed
     * @param bodyFactory A {@link ByteBodyFactory} for buffer-based methods
     * @return The body
     */
    public static CloseableByteBody create(InputStream stream, OptionalLong length, Executor ioExecutor, ByteBodyFactory bodyFactory) {
        ArgumentUtils.requireNonNull("stream", stream);
        ArgumentUtils.requireNonNull("length", length);
        ArgumentUtils.requireNonNull("ioExecutor", ioExecutor);
        ArgumentUtils.requireNonNull("bodyFactory", bodyFactory);
        return new InputStreamByteBody(new Context(length, ioExecutor, bodyFactory), ExtendedInputStream.wrap(stream));
    }

    @Override
    public CloseableByteBody allowDiscard() {
        if (stream == null) {
            failClaim();
        }
        stream.allowDiscard();
        return this;
    }

    @Override
    public void close() {
        if (stream != null) {
            recordClosed();
            stream.close();
            stream = null;
        }
    }

    @Override
    public CloseableByteBody split(SplitBackpressureMode backpressureMode) {
        if (stream == null) {
            failClaim();
        }
        StreamPair.Pair pair = StreamPair.createStreamPair(stream, backpressureMode);
        stream = pair.left();
        return new InputStreamByteBody(context, pair.right());
    }

    @Override
    public OptionalLong expectedLength() {
        return context.expectedLength();
    }

    @Override
    public ExtendedInputStream toInputStream() {
        ExtendedInputStream s = stream;
        if (s == null) {
            failClaim();
        }
        recordPrimaryOp();
        stream = null;
        BaseSharedBuffer.logClaim();
        return s;
    }

    @Override
    public Flux<byte[]> toByteArrayPublisher() {
        // the declared type of this method is a Flux: the reads run without Reactor
        return Flux.from(new StreamPublisher(toInputStream(), context.ioExecutor()));
    }

    @Override
    public Publisher<ReadBuffer> toReadBufferPublisher() {
        return BodyPublishers.map(new StreamPublisher(toInputStream(), context.ioExecutor()), context.bodyFactory.readBufferFactory()::adapt);
    }

    @Override
    public ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        ExtendedInputStream s = toInputStream();
        return ExecutionFlow.async(context.ioExecutor, () -> {
            try (ExtendedInputStream t = s) {
                return ExecutionFlow.just(context.bodyFactory().copyOf(t));
            } catch (Exception e) {
                return ExecutionFlow.error(e);
            }
        });
    }

    @Override
    public CloseableByteBody move() {
        return new InputStreamByteBody(context, toInputStream());
    }

    /**
     * Reads the stream on the IO executor as the bytes are requested, one read per requested
     * array, and closes the stream when it ends, fails or is cancelled.
     */
    private static final class StreamPublisher implements Publisher<byte[]>, Subscription, Runnable {
        private final ExtendedInputStream stream;
        private final Executor executor;
        private @Nullable Subscriber<? super byte[]> subscriber;

        // guarded by this
        private long demand;
        /**
         * A read task is scheduled or running.
         */
        private boolean reading;
        private boolean cancelled;
        private boolean done;

        StreamPublisher(ExtendedInputStream stream, Executor executor) {
            this.stream = stream;
            this.executor = executor;
        }

        @Override
        public void subscribe(Subscriber<? super byte[]> s) {
            synchronized (this) {
                if (subscriber != null) {
                    s.onSubscribe(new Subscription() {
                        @Override
                        public void request(long n) {
                        }

                        @Override
                        public void cancel() {
                        }
                    });
                    s.onError(new IllegalStateException("The bytes of a stream are published to a single subscriber"));
                    return;
                }
                subscriber = s;
            }
            s.onSubscribe(this);
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                return;
            }
            synchronized (this) {
                demand = Long.MAX_VALUE - demand < n ? Long.MAX_VALUE : demand + n;
                if (reading || done || cancelled) {
                    return;
                }
                reading = true;
            }
            executor.execute(this);
        }

        @Override
        public void run() {
            Subscriber<? super byte[]> s = subscriber;
            while (true) {
                synchronized (this) {
                    if (cancelled) {
                        reading = false;
                        break;
                    }
                    if (demand == 0) {
                        reading = false;
                        return;
                    }
                    if (demand != Long.MAX_VALUE) {
                        demand--;
                    }
                }
                byte @Nullable [] bytes;
                try {
                    bytes = stream.readSome();
                } catch (IOException e) {
                    finish();
                    if (s != null) {
                        s.onError(e);
                    }
                    return;
                }
                if (bytes == null) {
                    finish();
                    if (s != null) {
                        s.onComplete();
                    }
                    return;
                }
                synchronized (this) {
                    if (cancelled) {
                        // cancelled while the array was read: it is not delivered
                        reading = false;
                        break;
                    }
                }
                if (s != null) {
                    s.onNext(bytes);
                }
            }
            // cancelled while reading
            stream.close();
        }

        private void finish() {
            synchronized (this) {
                done = true;
                reading = false;
            }
            stream.close();
        }

        @Override
        public void cancel() {
            boolean closeNow;
            synchronized (this) {
                if (cancelled || done) {
                    return;
                }
                cancelled = true;
                // a running read closes the stream once it is done
                closeNow = !reading;
            }
            if (closeNow) {
                stream.close();
            }
        }
    }

    private record Context(
        OptionalLong expectedLength,
        Executor ioExecutor,
        ByteBodyFactory bodyFactory
    ) {
    }
}
