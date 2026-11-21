package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A refresh winds the pooled HTTP/2 connections down while requests are dispatched to them on
 * their event loop: a request that is dispatched to a connection just before the wind-down must
 * still be answered, and the connection closes only once its requests are done.
 */
class Http2ConnectionRefreshSpec extends Specification {

    private static final int REQUESTERS = 4
    private static final int REQUESTS = 1000
    private static final int REFRESHERS = 2

    def 'refreshing a client while h2c requests run fails none of them'() {
        given:
        ApplicationContext serverContext = ApplicationContext.run([
                'spec.name'                    : 'Http2ConnectionRefreshSpec',
                'micronaut.server.port'        : -1,
                'micronaut.server.http-version': '2.0',
        ])
        URL url = serverContext.getBean(EmbeddedServer).start().getURL()
        ApplicationContext clientContext = ApplicationContext.run([
                'micronaut.http.client.plaintext-mode': 'h2c_prior_knowledge',
                'micronaut.http.client.read-timeout'  : '5s',
        ])
        HttpClient client = clientContext.createBean(HttpClient, url)
        client.toBlocking().retrieve('/h2-refresh')

        def failures = new ConcurrentLinkedQueue<Throwable>()
        def running = new AtomicBoolean(true)
        def requesters = new CountDownLatch(REQUESTERS)

        when: 'several threads send requests while others keep refreshing the client'
        REQUESTERS.times {
            Thread.start {
                try {
                    for (int i = 0; i < REQUESTS && running.get(); i++) {
                        try {
                            String body = client.toBlocking().retrieve('/h2-refresh')
                            if (body != 'ok') {
                                failures.add(new IllegalStateException("Unexpected body: $body"))
                            }
                        } catch (Throwable t) {
                            failures.add(t)
                        }
                    }
                } finally {
                    requesters.countDown()
                }
            }
        }
        List<Thread> refreshers = (1..REFRESHERS).collect {
            Thread.start {
                while (requesters.count > 0) {
                    client.refresh()
                }
            }
        }
        boolean done = requesters.await(60, TimeUnit.SECONDS)
        running.set(false)
        refreshers*.join(10_000)

        then:
        done
        failures.countBy { it.class.simpleName } == [:]

        cleanup:
        clientContext?.close()
        serverContext?.close()
    }

    @Requires(property = 'spec.name', value = 'Http2ConnectionRefreshSpec')
    @Controller('/h2-refresh')
    static class RefreshController {
        @Get(produces = 'text/plain')
        String get() {
            'ok'
        }
    }
}
