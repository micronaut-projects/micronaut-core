package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.server.netty.FormRouteCompleter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class StreamingMultiObjectBody extends ManagedBody<Publisher<?>> implements MultiObjectBody {
    public StreamingMultiObjectBody(Publisher<?> publisher) {
        super(publisher);
    }

    @Override
    void release(Publisher<?> value) {
        // not subscribed, don't need to do anything
    }

    @Override
    public InputStream coerceToInputStream(ByteBufAllocator alloc) {
        PublisherAsStream publisherAsStream = new PublisherAsStream();
        claim().subscribe(publisherAsStream);
        return publisherAsStream;
    }

    @Override
    public Publisher<?> asPublisher() {
        return claim();
    }

    @Override
    public MultiObjectBody mapNotNull(Function<Object, Object> transform) {
        return next(new StreamingMultiObjectBody(Flux.from(prepareClaim()).mapNotNull(transform)));
    }

    @Override
    public void handleForm(FormRouteCompleter formRouteCompleter) {
        prepareClaim().subscribe(formRouteCompleter);
        next(formRouteCompleter);
    }

    private static final class PublisherAsStream extends InputStream implements Subscriber<Object> {
        private final Lock lock = new ReentrantLock();
        private final Condition newDataCondition = lock.newCondition();

        private Subscription subscription;
        private volatile ByteBuf buffer;
        private volatile boolean done = false;
        private Throwable failure = null;
        private boolean closed;

        @Override
        public int read() throws IOException {
            byte[] arr = new byte[1];
            int n = read(arr);
            return n == -1 ? -1 : arr[0] & 0xff;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            boolean requested = false;
            while (buffer == null) {
                if (done) {
                    if (failure == null) {
                        return -1;
                    } else {
                        throw new IOException(failure);
                    }
                }
                if (closed) {
                    throw new IOException("Channel closed");
                }

                if (!requested) {
                    subscription.request(1);
                    requested = true;
                }

                lock.lock();
                try {
                    if (buffer == null && !done) {
                        newDataCondition.awaitUninterruptibly();
                    }
                } finally {
                    lock.unlock();
                }
            }

            int toRead = Math.min(len, buffer.readableBytes());
            buffer.readBytes(b, off, toRead);
            if (!buffer.isReadable()) {
                buffer.release();
                buffer = null;
            }
            return toRead;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.subscription = s;
        }

        private void signalAll() {
            lock.lock();
            try {
                if (closed) {
                    if (buffer != null) {
                        buffer.release();
                        buffer = null;
                    }
                }
                newDataCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onNext(Object data) {
            if (data instanceof ByteBufHolder bbh) {
                data = bbh.content();
            }
            ByteBuf b = (ByteBuf) data;
            if (b.isReadable()) {
                if (this.buffer != null) {
                    throw new IllegalStateException("Double onNext?");
                }
                buffer = b;
                signalAll();
            } else {
                b.release();
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable t) {
            failure = t;
            done = true;
            signalAll();
        }

        @Override
        public void onComplete() {
            done = true;
            signalAll();
        }

        @Override
        public void close() throws IOException {
            if (subscription != null) {
                subscription.cancel();
            }
            lock.lock();
            try {
                closed = true;
                if (buffer != null) {
                    buffer.release();
                    buffer = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
