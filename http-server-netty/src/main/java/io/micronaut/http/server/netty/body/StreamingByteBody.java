/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.netty.reactive.HotObservable;
import io.micronaut.http.netty.stream.DelegateStreamedHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.FormDataHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorAsReactiveProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link ByteBody} implementation that wraps a
 * {@link io.micronaut.http.netty.stream.StreamedHttpRequest}.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class StreamingByteBody extends ManagedBody<Publisher<HttpContent>> implements ByteBody {
    private final long advertisedLength;

    StreamingByteBody(Publisher<HttpContent> publisher, long advertisedLength) {
        super(publisher);
        this.advertisedLength = advertisedLength;
    }

    @Override
    public MultiObjectBody processMulti(FormDataHttpContentProcessor processor) {
        return next(new StreamingMultiObjectBody(HttpContentProcessorAsReactiveProcessor.asPublisher(processor, prepareClaim())));
    }

    @Override
    public MultiObjectBody rawContent(HttpServerConfiguration configuration) {
        ImmediateByteBody.checkLength(configuration, advertisedLength);
        return next(new StreamingMultiObjectBody(new LengthCheckPublisher(configuration, prepareClaim())));
    }

    @Override
    public ExecutionFlow<ImmediateByteBody> buffer(ByteBufAllocator alloc) {
        IntermediateBuffering intermediateBuffering = new IntermediateBuffering(alloc);
        prepareClaim().subscribe(intermediateBuffering);
        next(intermediateBuffering);
        return intermediateBuffering.completion;
    }

    @Override
    public HttpRequest claimForReuse(HttpRequest request) {
        Publisher<HttpContent> publisher = prepareClaim();
        next(new HttpBodyReused());
        return new DelegateStreamedHttpRequest(request, publisher);
    }

    @Override
    void release(Publisher<HttpContent> value) {
        if (value instanceof HotObservable<?> hot) {
            hot.closeIfNoSubscriber();
        }
    }

    /**
     * Intermediate {@link HttpBody} after {@link #buffer(ByteBufAllocator)} has been called but
     * before all data is in.
     */
    private static final class IntermediateBuffering implements Subscriber<HttpContent>, HttpBody {
        private final DelayedExecutionFlow<ImmediateByteBody> completion = DelayedExecutionFlow.create();
        private final Lock lock = new ReentrantLock();
        private final ByteBufAllocator alloc;
        private Subscription subscription;
        private boolean discarded = false;
        private CompositeByteBuf composite;
        private ByteBuf single;
        private ImmediateByteBody next;

        private IntermediateBuffering(ByteBufAllocator alloc) {
            this.alloc = alloc;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
            this.subscription = s;
        }

        @Override
        public void onNext(HttpContent httpContent) {
            lock.lock();
            try {
                if (discarded) {
                    httpContent.release();
                    return;
                }
                if (composite != null) {
                    composite.addComponent(true, httpContent.content());
                } else if (single == null) {
                    single = httpContent.content();
                } else {
                    composite = alloc.compositeBuffer();
                    composite.addComponent(true, single);
                    composite.addComponent(true, httpContent.content());
                    single = null;
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onError(Throwable t) {
            discard();
            try {
                completion.completeExceptionally(t);
            } catch (IllegalStateException ignored) {
                // already completed
            }
        }

        @Override
        public void onComplete() {
            lock.lock();
            try {
                discarded = true;
                next = new ImmediateByteBody(composite == null ? single : composite);
                single = null;
                composite = null;
            } finally {
                lock.unlock();
            }
            completion.complete(next);
        }

        private void discard() {
            lock.lock();
            try {
                discarded = true;
                if (composite != null) {
                    composite.release();
                    composite = null;
                }
                if (single != null) {
                    single.release();
                    single = null;
                }
            } finally {
                lock.unlock();
            }
            if (next != null) {
                next.release();
            }
            if (subscription != null) {
                subscription.cancel();
            }
        }

        @Override
        public void release() {
            discard();
        }

        @Nullable
        @Override
        public HttpBody next() {
            return next;
        }
    }

    private static final class LengthCheckPublisher implements Publisher<ByteBuf>, Subscriber<HttpContent> {
        private final HttpServerConfiguration configuration;
        private final Publisher<HttpContent> upstream;
        private Subscriber<? super ByteBuf> downstream;
        private Subscription subscription;
        private long received = 0;
        private boolean exceeded = false;

        LengthCheckPublisher(HttpServerConfiguration configuration, Publisher<HttpContent> upstream) {
            this.configuration = configuration;
            this.upstream = upstream;
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuf> s) {
            downstream = s;
            upstream.subscribe(this);
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            downstream.onSubscribe(s);
        }

        @Override
        public void onNext(HttpContent httpContent) {
            if (exceeded) {
                httpContent.release();
                return;
            }

            ByteBuf buf = httpContent.content();

            received += buf.readableBytes();
            try {
                ImmediateByteBody.checkLength(configuration, received);
            } catch (ContentLengthExceededException fail) {
                exceeded = true;
                httpContent.release();
                downstream.onError(fail);
                subscription.cancel();
                return;
            }
            downstream.onNext(buf);
        }

        @Override
        public void onError(Throwable t) {
            if (!exceeded) {
                downstream.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (!exceeded) {
                downstream.onComplete();
            }
        }
    }
}
