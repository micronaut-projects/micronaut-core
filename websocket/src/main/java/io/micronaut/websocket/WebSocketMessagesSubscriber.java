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
package io.micronaut.websocket;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sends the messages of a publisher through a session, in order, see
 * {@link WebSocketSession#sendAllAsync(org.reactivestreams.Publisher, MediaType)}: the next message
 * is requested once the previous one was written, so the publisher produces no faster than the
 * connection writes, and not once the session is closed.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class WebSocketMessagesSubscriber implements Subscriber<Object> {

    private final WebSocketSession session;
    private final MediaType mediaType;
    private final CompletableFuture<Boolean> sent;
    /**
     * Turns the calls to the subscription into a loop, so that they are made one at a time
     * (rule 2.7), and a request made while a request is being made, e.g. by a publisher that emits
     * in {@link Subscription#request(long)} to a session that writes at once, does not recurse.
     */
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<@Nullable Subscription> subscription = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<?>> lastSend = new AtomicReference<>(CompletableFuture.completedFuture(null));
    /**
     * Whether the publisher signaled its completion or an error: it is not cancelled (rule 2.3).
     */
    private volatile boolean terminated;
    /**
     * Whether the subscription was cancelled: read and written by {@link #callSubscription()} only.
     */
    private boolean cancelled;

    /**
     * @param session   The session
     * @param mediaType The media type of the messages
     * @param sent      Completes with {@code true} when all the messages were sent, with
     *                  {@code false} when the session closed first: completing it before, e.g.
     *                  cancelling it, cancels the publisher. Completed already, it sends nothing
     *                  and cancels the publisher at once
     */
    public WebSocketMessagesSubscriber(WebSocketSession session, MediaType mediaType, CompletableFuture<Boolean> sent) {
        this.session = session;
        this.mediaType = mediaType;
        this.sent = sent;
        sent.whenComplete((ignored, error) -> callSubscription());
    }

    @Override
    public void onSubscribe(Subscription s) {
        if (!subscription.compareAndSet(null, s)) {
            s.cancel();
            return;
        }
        callSubscription();
    }

    @Override
    public void onNext(Object message) {
        if (sent.isDone()) {
            // cancelled: the publisher may still emit what it had
            return;
        }
        CompletableFuture<?> send;
        try {
            send = session.sendAsync(message, mediaType);
        } catch (RuntimeException e) {
            fail(e);
            return;
        }
        lastSend.set(send);
        send.whenComplete((ignored, error) -> {
            if (error == null) {
                callSubscription();
            } else {
                fail(error);
            }
        });
    }

    @Override
    public void onError(Throwable t) {
        terminated = true;
        sent.completeExceptionally(t);
    }

    @Override
    public void onComplete() {
        terminated = true;
        // a publisher may complete while its last message is being written
        Objects.requireNonNull(lastSend.get()).whenComplete((ignored, error) -> {
            if (error == null) {
                sent.complete(true);
            }
        });
    }

    private void fail(Throwable error) {
        if (session.isOpen()) {
            sent.completeExceptionally(error);
        } else {
            // the session closed: there is no one to send the rest to
            sent.complete(false);
        }
    }

    /**
     * Request the next message, or cancel once {@link #sent} completed before the publisher did.
     */
    private void callSubscription() {
        if (calls.getAndIncrement() != 0) {
            return;
        }
        do {
            Subscription s = subscription.get();
            if (s != null && !cancelled) {
                if (!sent.isDone() && !session.isOpen()) {
                    // the session closed: there is no one to send the rest to
                    sent.complete(false);
                }
                if (!sent.isDone()) {
                    s.request(1);
                } else if (!terminated) {
                    cancelled = true;
                    s.cancel();
                }
            }
        } while (calls.decrementAndGet() != 0);
    }
}
