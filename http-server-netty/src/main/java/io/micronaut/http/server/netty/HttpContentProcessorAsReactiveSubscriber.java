/*
 * Copyright 2017-2022 original authors
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

    /**
     * Called when new data is available, when there is an error, or when we are {@link #done}.
     *
     * @param out The new data, may be empty
     */
    abstract void notifyContentAvailable(Collection<Object> out);
}
