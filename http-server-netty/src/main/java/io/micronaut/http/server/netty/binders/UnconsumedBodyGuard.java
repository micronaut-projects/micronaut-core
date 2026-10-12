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
package io.micronaut.http.server.netty.binders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Registers streaming body arguments that claim the request body when they are bound, so that
 * the body is discarded if the route fails before it returns (see
 * {@link BasicHttpAttributes#discardUnconsumedBodies}). Without this, the data received so far
 * stays buffered and the connection stops reading.
 *
 * @since 5.2.10
 */
@Internal
public final class UnconsumedBodyGuard {
    private static final Logger LOG = LoggerFactory.getLogger(UnconsumedBodyGuard.class);

    private UnconsumedBodyGuard() {
    }

    /**
     * Discard the given publisher if the route fails before it returns.
     *
     * @param request   The request
     * @param publisher The publisher that was bound from the request body
     */
    public static void discardIfRouteFails(HttpRequest<?> request, Publisher<?> publisher) {
        // subscribe and cancel right away, this releases what was buffered so far and discards
        // the rest of the body
        BasicHttpAttributes.addUnconsumedBodyDiscard(request, () -> publisher.subscribe(new DiscardingSubscriber()));
    }

    /**
     * Discard the given stream if the route fails before it returns.
     *
     * @param request The request
     * @param stream  The stream that was bound from the request body
     */
    public static void discardIfRouteFails(HttpRequest<?> request, InputStream stream) {
        BasicHttpAttributes.addUnconsumedBodyDiscard(request, () -> {
            try {
                stream.close();
            } catch (IOException e) {
                LOG.debug("Failed to discard request body stream", e);
            }
        });
    }

    private static final class DiscardingSubscriber implements Subscriber<Object> {
        @Override
        public void onSubscribe(Subscription s) {
            s.cancel();
        }

        @Override
        public void onNext(Object o) {
            if (o instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    LOG.debug("Failed to release discarded request body item", e);
                }
            } else {
                ReferenceCountUtil.release(o);
            }
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onComplete() {
        }
    }
}
