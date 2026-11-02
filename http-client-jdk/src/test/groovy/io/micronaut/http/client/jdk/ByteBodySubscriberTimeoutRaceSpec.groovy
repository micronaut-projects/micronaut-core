package io.micronaut.http.client.jdk

import io.micronaut.http.body.stream.BodySizeLimits
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The read timeout of the JDK raw client body does not signal while the consumer handles a piece
 * of the body, nor counts that handling time as a stalled read.
 */
class ByteBodySubscriberTimeoutRaceSpec extends Specification {

    void "a consumer slower than the read timeout gets no concurrent or late signals"() {
        given:
        def subscriber = new ByteBodySubscriber(BodySizeLimits.UNLIMITED, Duration.ofMillis(100))
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            void request(long n) {
            }

            @Override
            void cancel() {
            }
        })
        def events = new CopyOnWriteArrayList<String>()
        def inOnNext = new AtomicBoolean()
        subscriber.body.toCompletableFuture().get().toByteArrayPublisher().subscribe(new Subscriber<byte[]>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(2)
            }

            @Override
            void onNext(byte[] bytes) {
                inOnNext.set(true)
                events.add('next')
                // longer than the read timeout
                Thread.sleep(400)
                inOnNext.set(false)
            }

            @Override
            void onError(Throwable t) {
                events.add(inOnNext.get() ? 'error-during-next' : 'error')
            }

            @Override
            void onComplete() {
                events.add('complete')
            }
        })

        when:
        subscriber.onNext([ByteBuffer.wrap('a'.bytes)])
        subscriber.onNext([ByteBuffer.wrap('b'.bytes)])
        subscriber.onComplete()
        Thread.sleep(300)

        then:
        events == ['next', 'next', 'complete']
    }

    void "a read timeout that fired before a piece of the body arrived does not abort the response"() {
        given:
        def subscriber = new ByteBodySubscriber(BodySizeLimits.UNLIMITED, Duration.ofMillis(100))
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            void request(long n) {
            }

            @Override
            void cancel() {
            }
        })
        def events = new CopyOnWriteArrayList<String>()
        subscriber.body.toCompletableFuture().get().toByteArrayPublisher().subscribe(new Subscriber<byte[]>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(2)
            }

            @Override
            void onNext(byte[] bytes) {
                events.add('next')
            }

            @Override
            void onError(Throwable t) {
                events.add('error')
            }

            @Override
            void onComplete() {
                events.add('complete')
            }
        })

        when: 'the timer fires and waits for the monitor while a piece of the body arrives'
        synchronized (subscriber) {
            Thread.sleep(300)
            subscriber.onNext([ByteBuffer.wrap('a'.bytes)])
        }
        // shorter than the timeout armed again after the piece
        Thread.sleep(30)

        then:
        events == ['next']

        cleanup:
        subscriber.onComplete()
    }
}
