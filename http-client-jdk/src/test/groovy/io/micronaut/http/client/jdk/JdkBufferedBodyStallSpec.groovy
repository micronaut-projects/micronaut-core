package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.ReadTimeoutException
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * A buffered response body that stalls fails with a read timeout, not with a truncated body.
 */
class JdkBufferedBodyStallSpec extends Specification {

    void "a stalled buffered body fails with a read timeout"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '1s'])
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())

        when:
        def pending = Mono.from(client.exchange(HttpRequest.GET('/stalled'), String)).toFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)
        connection.awaitRequest(10)
        connection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 10\r\n\r\nab')
        pending.get(8, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof ReadTimeoutException

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }
}
