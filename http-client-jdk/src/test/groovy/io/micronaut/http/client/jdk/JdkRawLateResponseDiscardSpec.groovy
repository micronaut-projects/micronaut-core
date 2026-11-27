package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.client.RawHttpClient
import reactor.core.Disposable
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A response of the JDK raw client that arrives after the subscriber cancelled is closed: nobody
 * takes it, and it holds the connection.
 */
class JdkRawLateResponseDiscardSpec extends Specification {

    static final AtomicReference<Runnable> CANCEL = new AtomicReference<>()
    static final AtomicBoolean CANCELLED = new AtomicBoolean()

    void "a #api response cancelled from another thread after the exchange completed is closed"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        // the read timeout does not close the connection while the test waits
        ApplicationContext ctx = ApplicationContext.run([
            'micronaut.http.client.read-timeout': '60s',
            'spec.name'                         : 'JdkRawLateResponseDiscardSpec'
        ])
        AtomicReference<Object> delivered = new AtomicReference<>()
        CANCELLED.set(false)

        when:
        def request = HttpRequest.GET(upstream.uri('/late'))
        def publisher = api == 'raw'
            ? ctx.getBean(RawHttpClient).exchange(request, (CloseableByteBody) null, (Thread) null)
            : ctx.getBean(ProxyHttpClient).proxy(request)
        Disposable subscription = Mono.from(publisher).subscribe { delivered.set(it) }
        CANCEL.set(subscription::dispose)
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)

        then:
        connection != null
        connection.awaitRequest(10)

        when: 'a body that does not end: only closing the response releases the connection'
        connection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\n0123456789')

        then: 'the response is closed, which aborts its request'
        connection.awaitClosed(10)
        CANCELLED.get()
        delivered.get() == null

        cleanup:
        ctx?.close()
        upstream?.close()

        where:
        api << ['raw', 'proxy']
    }

    /**
     * Runs once the exchange completed with the response, before the subscriber gets it: the
     * subscriber cancels from another thread right there.
     */
    @ClientFilter("/late")
    @Requires(property = "spec.name", value = "JdkRawLateResponseDiscardSpec")
    static class CancellingFilter {
        @ResponseFilter
        void cancel(HttpResponse<?> response) {
            if (CANCELLED.compareAndSet(false, true)) {
                Thread canceller = new Thread(CANCEL.get(), "late-response-cancel")
                canceller.start()
                canceller.join()
            }
        }
    }
}
