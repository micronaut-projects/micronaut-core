package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Cancelling a publisher stream of the JDK client (dataStream, exchangeStream) in the middle of
 * the body, as LegacyStreamCancellationTest does for the Netty client: the subscriber gets no
 * signal after it cancelled, the connection is closed, and the client goes on.
 */
class JdkStreamCancellationSpec extends Specification {

    void "cancelling a #api stream in the middle of the body"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '60s'])
        StreamingHttpClient client = (StreamingHttpClient) ctx.createBean(HttpClient, upstream.uri('/').toURL())
        AtomicReference<Subscription> subscription = new AtomicReference<>()
        CountDownLatch first = new CountDownLatch(1)
        AtomicBoolean cancelled = new AtomicBoolean()
        AtomicBoolean signalledAfterCancel = new AtomicBoolean()
        Subscriber<Object> subscriber = new Subscriber<Object>() {
            void onSubscribe(Subscription s) {
                subscription.set(s)
                s.request(1)
            }

            void onNext(Object o) {
                if (cancelled.get()) {
                    signalledAfterCancel.set(true)
                }
                first.countDown()
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
        Publisher<?> stream = api == 'dataStream'
            ? client.dataStream(HttpRequest.GET('/pending'))
            : client.exchangeStream(HttpRequest.GET('/pending'))
        stream.subscribe(subscriber)
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)

        then:
        connection != null
        connection.awaitRequest(10)

        when:
        connection.write('HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: 100\r\n\r\n0123456789')

        then:
        first.await(10, TimeUnit.SECONDS)

        when:
        cancelled.set(true)
        subscription.get().cancel()
        // more of the body arrives after the cancel
        try {
            connection.write('0123456789')
        } catch (IOException ignored) {
            // closed already
        }

        then: 'the connection is closed'
        connection.awaitClosed(5)

        when: 'the client goes on'
        def next = Mono.from(((HttpClient) client).exchange(HttpRequest.GET('/next'), String)).toFuture()
        RawSocketUpstream.Connection nextConnection = upstream.nextConnection(10)
        nextConnection.awaitRequest(10)
        nextConnection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok')

        then:
        next.get(10, TimeUnit.SECONDS).body() == 'ok'

        and: 'the cancelled subscriber got no signal'
        !signalledAfterCancel.get()

        cleanup:
        ((HttpClient) client)?.close()
        ctx.close()
        upstream.close()

        where:
        api << ['dataStream', 'exchangeStream']
    }
}
