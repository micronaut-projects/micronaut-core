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
package io.micronaut.web.router.websocket;

import io.micronaut.core.io.buffer.ReferenceCounted;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The messages of a connection as a stream: a message is done, and the connection reads on, once
 * the subscriber received it.
 */
class WebSocketMessageStreamTest {

    @Test
    void aMessageIsDoneOnceTheSubscriberReceivedIt() {
        WebSocketMessageStream<String> stream = stream();
        CompletionStage<?> first = stream.offer("first");
        CompletionStage<?> second = stream.offer("second");
        assertFalse(done(first));

        Recorder recorder = new Recorder();
        stream.subscribe(recorder);
        assertEquals(List.of(), recorder.signals);

        recorder.subscription.request(1);
        assertEquals(List.of("first"), recorder.signals);
        assertTrue(done(first));
        assertFalse(done(second));

        recorder.subscription.request(1);
        assertEquals(List.of("first", "second"), recorder.signals);
        assertTrue(done(second));

        // with demand, a message is received as it is read
        recorder.subscription.request(Long.MAX_VALUE);
        assertTrue(done(stream.offer("third")));
        assertEquals(List.of("first", "second", "third"), recorder.signals);
    }

    @Test
    void theStreamCompletesOnceTheSubscriberReceivedTheMessagesThatWereRead() {
        WebSocketMessageStream<String> stream = stream();
        Recorder recorder = new Recorder();
        stream.subscribe(recorder);
        stream.offer("last");
        stream.complete();
        assertEquals(List.of(), recorder.signals);

        recorder.subscription.request(1);
        assertEquals(List.of("last", "complete"), recorder.signals);
        // the connection closed: no more messages
        assertTrue(done(stream.offer("late")));
        assertEquals(List.of("last", "complete"), recorder.signals);
    }

    @Test
    void aSubscriberThatCancelsDiscardsTheMessages() {
        WebSocketMessageStream<String> stream = stream();
        Recorder recorder = new Recorder();
        stream.subscribe(recorder);
        CompletionStage<?> unread = stream.offer("unread");
        recorder.subscription.cancel();
        // discarded, so that the connection reads on
        assertTrue(done(unread));
        assertTrue(done(stream.offer("after")));
        recorder.subscription.request(1);
        stream.complete();
        assertEquals(List.of(), recorder.signals);
    }

    @Test
    void theStreamHasASingleSubscriber() {
        WebSocketMessageStream<String> stream = stream();
        stream.subscribe(new Recorder());
        Recorder second = new Recorder();
        stream.subscribe(second);
        assertInstanceOf(IllegalStateException.class, second.error);
    }

    @Test
    void aRequestForNoMessageFailsTheSubscriber() {
        WebSocketMessageStream<String> stream = stream();
        Recorder recorder = new Recorder();
        stream.subscribe(recorder);
        CompletionStage<?> unread = stream.offer("unread");
        recorder.subscription.request(0);
        assertInstanceOf(IllegalArgumentException.class, recorder.error);
        assertTrue(done(unread));
    }

    @Test
    void aSubscriberThatFailsASignalIsCancelledAndItsErrorHandled() {
        List<Throwable> errors = new ArrayList<>();
        WebSocketMessageStream<String> stream = new WebSocketMessageStream<>(Runnable::run, errors::add);
        Recorder failing = new Recorder() {
            @Override
            public void onNext(String message) {
                throw new IllegalStateException("failed " + message);
            }
        };
        stream.subscribe(failing);
        failing.subscription.request(2);
        assertTrue(done(stream.offer("first")));
        // cancelled: the messages that follow are discarded
        assertTrue(done(stream.offer("second")));
        assertEquals(1, errors.size());
        assertEquals("failed first", errors.getFirst().getMessage());
    }

    @Test
    void theSubscriberIsSignaledOnTheExecutor() {
        List<Runnable> tasks = new ArrayList<>();
        WebSocketMessageStream<String> stream = new WebSocketMessageStream<>(tasks::add, error -> { });
        Recorder recorder = new Recorder();
        stream.subscribe(recorder);
        recorder.subscription.request(1);
        CompletionStage<?> message = stream.offer("message");
        assertEquals(List.of(), recorder.signals);
        assertFalse(done(message));
        new ArrayList<>(tasks).forEach(Runnable::run);
        assertEquals(List.of("message"), recorder.signals);
        assertTrue(done(message));
    }

    @Test
    void discardingTheMessagesCompletesTheirStages() {
        WebSocketMessageStream<String> stream = stream();
        CompletionStage<?> unread = stream.offer("unread");
        assertFalse(stream.hasSubscriber());
        stream.discard();
        assertTrue(done(unread));
        assertTrue(done(stream.offer("after")));
    }

    @Test
    void theMessagesASubscriberThatCancelsDoesNotReceiveAreReleased() {
        WebSocketMessageStream<Counted> stream = new WebSocketMessageStream<>(Runnable::run, error -> {
            throw new AssertionError("unexpected", error);
        });
        Counted received = new Counted();
        Counted unread = new Counted();
        Counted after = new Counted();
        List<Counted> signals = new ArrayList<>();
        Subscription[] subscription = new Subscription[1];
        stream.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscription[0] = s;
            }

            @Override
            public void onNext(Counted message) {
                signals.add(message);
            }

            @Override
            public void onError(Throwable t) {
                // not expected
            }

            @Override
            public void onComplete() {
                // not expected
            }
        });
        subscription[0].request(1);
        stream.offer(received);
        stream.offer(unread);
        subscription[0].cancel();
        stream.offer(after);
        assertEquals(List.of(received), signals);
        // the subscriber owns what it received
        assertEquals(1, received.refCnt);
        assertEquals(0, unread.refCnt);
        assertEquals(0, after.refCnt);
    }

    @Test
    void theMessagesDiscardedWithoutASubscriberOrAfterTheCloseAreReleased() {
        List<Object> released = new ArrayList<>();
        WebSocketMessageStream<Object> stream = new WebSocketMessageStream<>(Runnable::run, error -> {
            throw new AssertionError("unexpected", error);
        }, released::add);
        stream.offer("unread");
        stream.discard();
        stream.offer("after");
        assertEquals(List.of("unread", "after"), released);

        Counted late = new Counted();
        WebSocketMessageStream<Counted> closed = new WebSocketMessageStream<>(Runnable::run, error -> {
            throw new AssertionError("unexpected", error);
        });
        closed.complete();
        closed.offer(late);
        assertEquals(0, late.refCnt);
    }

    /**
     * A reference counted message.
     */
    private static final class Counted implements ReferenceCounted {
        private int refCnt = 1;

        @Override
        public ReferenceCounted retain() {
            refCnt++;
            return this;
        }

        @Override
        public boolean release() {
            refCnt--;
            return refCnt == 0;
        }
    }

    private static WebSocketMessageStream<String> stream() {
        return new WebSocketMessageStream<>(Runnable::run, error -> {
            throw new AssertionError("unexpected", error);
        });
    }

    private static boolean done(CompletionStage<?> stage) {
        return stage.toCompletableFuture().isDone();
    }

    /**
     * Records the signals of a stream.
     */
    private static class Recorder implements Subscriber<String> {
        private final List<String> signals = new ArrayList<>();
        private Subscription subscription;
        private Throwable error;

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
        }

        @Override
        public void onNext(String message) {
            signals.add(message);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
        }

        @Override
        public void onComplete() {
            signals.add("complete");
        }
    }
}
