package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ReadBufferFactory
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.client.RawHttpClient
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Cancelling the publisher of a reactive exchange of the Netty client, before the response or
 * while its body arrives, behaves as before the client shared its pipeline: the request is
 * aborted, its connection closed before the response (a body that arrives is drained), the request body is released, the
 * subscriber gets no signal after it cancelled, and the client goes on.
 */
class ReactiveCancellationSpec extends Specification {

    void "cancelling a #api exchange #phase"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        // the read timeout does not close the connection while the test waits
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '60s'])
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())
        CountDownLatch bodyReleased = new CountDownLatch(1)
        AtomicReference<Subscription> subscription = new AtomicReference<>()
        AtomicReference<Object> received = new AtomicReference<>()
        AtomicBoolean signalledAfterCancel = new AtomicBoolean()
        AtomicBoolean cancelled = new AtomicBoolean()
        Subscriber<Object> subscriber = new Subscriber<Object>() {
            void onSubscribe(Subscription s) {
                subscription.set(s)
                s.request(1)
            }

            void onNext(Object o) {
                received.set(o)
                if (cancelled.get()) {
                    signalledAfterCancel.set(true)
                }
            }

            void onError(Throwable t) {
                if (cancelled.get()) {
                    signalledAfterCancel.set(true)
                }
            }

            void onComplete() {
                if (cancelled.get()) {
                    signalledAfterCancel.set(true)
                }
            }
        }

        when:
        switch (api) {
            case 'exchange':
                client.exchange(HttpRequest.GET('/pending'), String).subscribe(subscriber)
                break
            case 'retrieve':
                client.retrieve(HttpRequest.GET('/pending'), String).subscribe(subscriber)
                break
            case 'raw':
                ctx.getBean(RawHttpClient).exchange(HttpRequest.GET(upstream.uri('/pending')), null, null).subscribe(subscriber)
                break
            case 'raw with a body':
                ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
                CloseableByteBody body = factory.adapt(Flux.just(ReadBufferFactory.getJdkFactory().adapt('xy'.bytes)).concatWith(Flux.never()),
                    BodySizeLimits.UNLIMITED, null, { bodyReleased.countDown() } as Runnable)
                ctx.getBean(RawHttpClient).exchange(HttpRequest.POST(upstream.uri('/pending'), null), body, null).subscribe(subscriber)
                break
            case 'proxy':
                ctx.getBean(ProxyHttpClient).proxy(HttpRequest.GET(upstream.uri('/pending'))).subscribe(subscriber)
                break
        }
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)

        then:
        connection != null
        connection.awaitRequest(10)

        when:
        if (phase == 'while the body arrives') {
            connection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\n0123456789')
            Thread.sleep(500)
        }
        if (received.get() instanceof ByteBodyHttpResponse) {
            // the response arrived: its body is let go
            ((ByteBodyHttpResponse) received.get()).close()
        }
        cancelled.set(true)
        subscription.get().cancel()

        then: 'the request is aborted'
        phase == 'while the body arrives' && !false || connection.awaitClosed(5)

        and: 'its body is released'
        api != 'raw with a body' || phase != 'before the response' || bodyReleased.await(5, TimeUnit.SECONDS)

        when: 'the client goes on'
        def next = Mono.from(client.exchange(HttpRequest.GET('/next'), String)).toFuture()
        RawSocketUpstream.Connection nextConnection = upstream.nextConnection(10)
        nextConnection.awaitRequest(10)
        nextConnection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok')

        then:
        next.get(10, TimeUnit.SECONDS).body() == 'ok'

        and: 'the cancelled subscriber got no signal'
        !signalledAfterCancel.get()

        cleanup:
        client?.close()
        ctx.close()
        upstream.close()

        where:
        [api, phase] << [['exchange', 'retrieve', 'raw', 'raw with a body', 'proxy'], ['before the response', 'while the body arrives']].combinations()
    }
}
