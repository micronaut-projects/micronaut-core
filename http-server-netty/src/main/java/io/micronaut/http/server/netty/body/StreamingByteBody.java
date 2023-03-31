package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorAsReactiveProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.HttpContent;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Internal
public final class StreamingByteBody extends ManagedBody<Publisher<HttpContent>> implements ByteBody {
    StreamingByteBody(Publisher<HttpContent> publisher) {
        super(publisher);
    }

    @Override
    public MultiObjectBody processMulti(HttpContentProcessor processor) {
        return next(new StreamingMultiObjectBody(HttpContentProcessorAsReactiveProcessor.asPublisher(processor, prepareClaim())));
    }

    @Override
    public ExecutionFlow<ImmediateByteBody> buffer(ByteBufAllocator alloc) {
        IntermediateBuffering intermediateBuffering = new IntermediateBuffering(alloc);
        prepareClaim().subscribe(intermediateBuffering);
        next(intermediateBuffering);
        return intermediateBuffering.completion;
    }

    @Override
    void release(Publisher<HttpContent> value) {
        // not subscribed, don't need to do anything
    }

    private static class IntermediateBuffering implements Subscriber<HttpContent>, HttpBody {
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
}
