package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBufHolder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@link Subscriber} that processes the incoming bytes, and calls
 * {@link #notifyContentAvailable} with any new output.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
abstract class HttpContentProcessorAsReactiveSubscriber implements Subscriber<ByteBufHolder> {
    final HttpContentProcessor processor;

    final List<Object> outBuffer = new ArrayList<>(1);

    Subscription upstream;
    Throwable failure;
    boolean done;

    HttpContentProcessorAsReactiveSubscriber(HttpContentProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (upstream != null) {
            throw new IllegalStateException("Only one upstream subscription allowed");
        }
        upstream = s;
    }

    @Override
    public void onNext(ByteBufHolder holder) {
        outBuffer.clear();
        try {
            processor.add(holder, outBuffer);
        } catch (Throwable t) {
            failure = t;
        }
        notifyContentAvailable(outBuffer);
    }

    @Override
    public void onError(Throwable t) {
        try {
            processor.cancel();
        } catch (Throwable other) {
            t.addSuppressed(other);
        }
        failure = t;
        done = true;
        // cancel does not produce data, but we still need to do this call to process the
        // failure
        notifyContentAvailable(Collections.emptyList());
    }

    @Override
    public void onComplete() {
        outBuffer.clear();
        try {
            processor.complete(outBuffer);
        } catch (Throwable t) {
            failure = t;
        }
        done = true;
        notifyContentAvailable(outBuffer);
    }

    final void requestContent() {
        upstream.request(1);
    }

    abstract void notifyContentAvailable(Collection<Object> out);
}
