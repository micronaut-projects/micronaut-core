package io.micronaut.websocket

import io.micronaut.http.MediaType
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

/**
 * {@link WebSocketSession#sendAllAsync}: the messages of a publisher, one after the other.
 */
class WebSocketMessagesSubscriberSpec extends Specification {

    void "the messages are sent in order, and a publisher that emits as it is requested does not recurse"() {
        given:
        List<Object> sent = []
        WebSocketSession session = session(true) { message ->
            sent << message
            CompletableFuture.completedFuture(message)
        }
        Messages messages = new Messages((1..100_000).toList(), true)

        when:
        CompletableFuture<Void> future = sendAll(session, messages)

        then:
        future.isDone()
        !future.isCompletedExceptionally()
        sent == (1..100_000).toList()
    }

    void "the next message is requested once the previous one was written"() {
        given:
        List<CompletableFuture<Object>> writes = []
        WebSocketSession session = session(true) { message ->
            CompletableFuture<Object> write = new CompletableFuture<>()
            writes << write
            write
        }
        Messages messages = new Messages(['a', 'b'], true)

        when:
        CompletableFuture<Void> future = sendAll(session, messages)

        then:
        writes.size() == 1
        messages.requests == 1

        when:
        writes[0].complete('a')

        then:
        writes.size() == 2
        messages.requests == 2
        !future.isDone()

        when: 'the publisher completed with the last message, which is written later'
        writes[1].complete('b')

        then:
        future.isDone()
        !future.isCompletedExceptionally()
    }

    void "the future fails when the publisher or a send fails, and completes when the session closed"() {
        given:
        boolean open = true
        WebSocketSession failing = session({ open }) { message ->
            CompletableFuture.failedFuture(new IllegalStateException("send failed"))
        }

        when:
        CompletableFuture<Void> failedSend = sendAll(failing, new Messages(['a'], true))

        then:
        failedSend.isCompletedExceptionally()

        when:
        open = false
        CompletableFuture<Void> closed = sendAll(failing, new Messages(['a'], true))

        then:
        closed.isDone()
        !closed.isCompletedExceptionally()

        when:
        CompletableFuture<Void> failedPublisher = sendAll(session(true) { CompletableFuture.completedFuture(it) }, new Messages([], true, new IllegalStateException("publisher failed")))

        then:
        failedPublisher.isCompletedExceptionally()
    }

    void "completing the future cancels the publisher"() {
        given:
        WebSocketSession session = session(true) { new CompletableFuture<Object>() }
        Messages messages = new Messages(['a', 'b'], true)

        when:
        CompletableFuture<Void> future = sendAll(session, messages)
        future.cancel(false)

        then:
        messages.cancelled
    }

    void "a publisher that completed is not cancelled, and nothing is sent once the future completed"() {
        given:
        List<Object> sent = []
        WebSocketSession session = session(true) { message ->
            sent << message
            CompletableFuture.completedFuture(message)
        }
        Messages completing = new Messages(['a'], true)

        when:
        CompletableFuture<Void> future = sendAll(session, completing)

        then: 'rule 2.3'
        future.isDone()
        !completing.cancelled

        when: 'a publisher that emits what it had after the cancel'
        Subscriber<Object> subscriber = null
        Publisher<Object> late = { Subscriber<Object> s ->
            subscriber = s
            s.onSubscribe(new Subscription() {
                @Override
                void request(long n) {
                }

                @Override
                void cancel() {
                }
            })
        } as Publisher<Object>
        CompletableFuture<Void> cancelled = sendAll(session, late)
        cancelled.cancel(false)
        subscriber.onNext('late')

        then:
        sent == ['a']
    }

    private static CompletableFuture<Void> sendAll(WebSocketSession session, Publisher<?> messages) {
        CompletableFuture<Void> sent = new CompletableFuture<>()
        messages.subscribe(new WebSocketMessagesSubscriber(session, MediaType.TEXT_PLAIN_TYPE, sent))
        sent
    }

    private static WebSocketSession session(boolean open, Closure<CompletableFuture<Object>> send) {
        session({ open }, send)
    }

    private static WebSocketSession session(Closure<Boolean> open, Closure<CompletableFuture<Object>> send) {
        [
            isOpen   : { -> open.call() },
            sendAsync: { Object message, MediaType mediaType -> send.call(message) }
        ] as WebSocketSession
    }

    /**
     * Emits its messages as they are requested, in the request call.
     */
    static final class Messages implements Publisher<Object> {
        final List<Object> messages
        final boolean complete
        final Throwable error
        long requested
        int requests
        boolean cancelled
        private int index
        private boolean emitting

        Messages(List<Object> messages, boolean complete, Throwable error = null) {
            this.messages = messages
            this.complete = complete
            this.error = error
        }

        @Override
        void subscribe(Subscriber<? super Object> subscriber) {
            Messages publisher = this
            subscriber.onSubscribe(new Subscription() {
                @Override
                void request(long n) {
                    publisher.requests++
                    publisher.requested += n
                    if (publisher.emitting) {
                        return
                    }
                    publisher.emitting = true
                    while (publisher.requested > 0 && publisher.index < publisher.messages.size() && !publisher.cancelled) {
                        publisher.requested--
                        subscriber.onNext(publisher.messages[publisher.index++])
                        if (publisher.index == publisher.messages.size() && publisher.complete) {
                            subscriber.onComplete()
                        }
                    }
                    if (publisher.error != null) {
                        subscriber.onError(publisher.error)
                    }
                    publisher.emitting = false
                }

                @Override
                void cancel() {
                    publisher.cancelled = true
                }
            })
        }
    }
}
