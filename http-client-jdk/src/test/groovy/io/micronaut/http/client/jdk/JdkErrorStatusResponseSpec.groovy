package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * A JDK client that does not fail on an error status returns the response of the error status,
 * its body decoded into the body type, as it did before it shared the pipeline of the clients.
 */
class JdkErrorStatusResponseSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkErrorStatusResponseSpec'])

    void "exchange returns the response of a #status status when exception-on-error-status is false"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.exception-on-error-status': false])
        HttpClient client = ctx.createBean(HttpClient, server.URL)

        when:
        HttpResponse<String> reactive = Mono.from(client.exchange(HttpRequest.GET("/error-status/$status"), String)).block()
        HttpResponse<String> blocking = client.toBlocking().exchange(HttpRequest.GET("/error-status/$status"), String)
        HttpResponse<String> async = client.toAsync().exchange(HttpRequest.GET("/error-status/$status"), String).toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        [reactive, blocking, async].every { it.code() == status && it.body() == "status $status".toString() }

        cleanup:
        client.close()
        ctx.close()

        where:
        status << [404, 500]
    }

    void "retrieve returns the body of an error status when exception-on-error-status is false"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.exception-on-error-status': false])
        HttpClient client = ctx.createBean(HttpClient, server.URL)

        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/error-status/404')) == 'status 404'
        Mono.from(client.retrieve(HttpRequest.GET('/error-status/404'), String)).block() == 'status 404'

        cleanup:
        client.close()
        ctx.close()
    }

    void "a status without a reason is returned when exception-on-error-status is false"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.exception-on-error-status': false])
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())

        when:
        def pending = Mono.from(client.exchange(HttpRequest.GET('/custom'), String)).toFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)
        connection.awaitRequest(10)
        connection.write('HTTP/1.1 599 Custom\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nno')
        HttpResponse<String> response = pending.get(10, TimeUnit.SECONDS)

        then:
        response.code() == 599
        response.body() == 'no'

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }

    void "a status without a reason fails with a response exception"() {
        given:
        RawSocketUpstream upstream = new RawSocketUpstream()
        ApplicationContext ctx = ApplicationContext.run()
        HttpClient client = ctx.createBean(HttpClient, upstream.uri('/').toURL())

        when:
        def pending = Mono.from(client.exchange(HttpRequest.GET('/custom'), String)).toFuture()
        RawSocketUpstream.Connection connection = upstream.nextConnection(10)
        connection.awaitRequest(10)
        connection.write('HTTP/1.1 599 Custom\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nno')
        pending.get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(java.util.concurrent.ExecutionException)
        e.cause instanceof HttpClientResponseException
        ((HttpClientResponseException) e.cause).response.code() == 599

        cleanup:
        client.close()
        ctx.close()
        upstream.close()
    }

    @Requires(property = 'spec.name', value = 'JdkErrorStatusResponseSpec')
    @Controller('/error-status')
    static class ErrorStatusController {
        @Get(uri = '/{status}', produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> status(int status) {
            return HttpResponse.<String> status(io.micronaut.http.HttpStatus.valueOf(status)).body("status $status".toString())
        }
    }
}
