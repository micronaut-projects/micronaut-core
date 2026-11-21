package io.micronaut.http.client.jdk

import io.micronaut.http.body.stream.BodySizeLimits
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Flow

/**
 * A read timeout of the JDK raw client body that fired before a piece of the body arrived does not
 * abort the response.
 */
class ByteBodySubscriberTimeoutRaceSpec extends Specification {

    // long enough for a loaded build machine to deliver the first piece before it elapses
    private static final long READ_TIMEOUT_MILLIS = 500

    void "a read timeout that fired before a piece of the body arrived does not abort the response"() {
        given:
        def subscriber = new ByteBodySubscriber(BodySizeLimits.UNLIMITED, false, null, Duration.ofMillis(READ_TIMEOUT_MILLIS))
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
            Thread.sleep(READ_TIMEOUT_MILLIS * 2)
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
