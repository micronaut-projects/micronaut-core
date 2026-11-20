package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The body of a redirect that the client follows is discarded within the read timeout: the
 * request timeout of the JDK client ends when the headers arrive.
 */
class JdkRedirectBodyStallSpec extends Specification {

    void "a stalled body of a followed redirect fails within the read timeout"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '1s'])
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())

        when:
        def pending = Mono.from(client.exchange(HttpRequest.GET('/redirect'), String)).toFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)
        connection.awaitRequest(10)
        connection.write('HTTP/1.1 302 Found\r\nLocation: /target\r\nContent-Length: 10\r\n\r\nab')
        pending.get(8, TimeUnit.SECONDS)

        then:
        thrown(ExecutionException)

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }
}
