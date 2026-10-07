package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Cancelling the future of an exchange of the async view of the JDK client cancels the exchange:
 * the request is aborted and its connection closed, and the client goes on.
 */
class JdkAsyncExchangeCancellationSpec extends Specification {

    void "cancelling the future of an async exchange before the response aborts the request"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        // the read timeout does not close the connection while the test waits
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '60s'])
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())

        when:
        CompletableFuture<?> future = client.toAsync().exchange(HttpRequest.GET('/pending'), String).toCompletableFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)

        then:
        connection != null
        connection.awaitRequest(10)

        when:
        future.cancel(true)

        then: 'the connection of the cancelled request is closed'
        connection.awaitClosed(5)

        when: 'the client goes on'
        CompletableFuture<?> next = client.toAsync().exchange(HttpRequest.GET('/next'), String).toCompletableFuture()
        RawSocketUpstream.Connection nextConnection = upstream.nextConnection(10)
        nextConnection.awaitRequest(10)
        nextConnection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok')

        then:
        next.get(10, TimeUnit.SECONDS).body() == 'ok'

        cleanup:
        client?.close()
        ctx.close()
        upstream.close()
    }
}
