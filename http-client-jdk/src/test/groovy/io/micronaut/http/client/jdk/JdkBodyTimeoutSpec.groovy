package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.ReadTimeoutException
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The timeouts of a response body read whole by the JDK client: the read timeout applies to the
 * response headers, and a body that keeps coming is read to its end, as before the JDK client
 * shared the pipeline of the clients. The request timeout is not applied, as before, unless the
 * configuration opts in: then a request timeout that elapses while the body is read says so.
 */
class JdkBodyTimeoutSpec extends Specification {

    void "a body that keeps coming for longer than the read timeout is read"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '1s'])
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())

        when:
        def pending = Mono.from(client.exchange(HttpRequest.GET('/slow'), String)).toFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)
        connection.awaitRequest(10)
        connection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 6\r\n\r\n')
        for (String piece : ['a', 'b', 'c', 'd', 'e', 'f']) {
            // the whole body takes longer than the read timeout plus a second
            Thread.sleep(500)
            connection.write(piece)
        }
        HttpResponse<String> response = pending.get(10, TimeUnit.SECONDS)

        then:
        response.body() == 'abcdef'

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }

    void "the request timeout is not applied by default"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.http.client.read-timeout'   : '30s',
                'micronaut.http.client.request-timeout': '1s',
        ])
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())

        when:
        def pending = Mono.from(client.exchange(HttpRequest.GET('/slow'), String)).toFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)
        connection.awaitRequest(10)
        connection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\n\r\nab')
        // longer than the request timeout
        Thread.sleep(2000)
        connection.write('cd')
        HttpResponse<String> response = pending.get(10, TimeUnit.SECONDS)

        then:
        response.body() == 'abcd'

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }

    void "an applied request timeout that elapses while the body is read says so"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.http.client.read-timeout'             : '30s',
                'micronaut.http.client.request-timeout'          : '1s',
                'micronaut.http.client.jdk.apply-request-timeout': true,
        ])
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())

        when:
        def pending = Mono.from(client.exchange(HttpRequest.GET('/stalled'), String)).toFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)
        connection.awaitRequest(10)
        connection.write('HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 6\r\n\r\nab')
        pending.get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof ReadTimeoutException
        ((ReadTimeoutException) e.cause).headersReceived
        e.cause.message == 'Read Timeout while reading the response body'

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }
}
