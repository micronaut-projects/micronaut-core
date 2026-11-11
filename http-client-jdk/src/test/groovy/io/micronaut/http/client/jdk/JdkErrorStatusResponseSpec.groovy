package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ExecutionException
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

    void "a stream of an error status fails with a response exception when exception-on-error-status is false"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.exception-on-error-status': false])
        StreamingHttpClient client = ctx.createBean(HttpClient, server.URL) as StreamingHttpClient

        when:
        Flux.from(client.jsonStream(HttpRequest.GET('/error-status/json/500'), Argument.of(Map), Argument.of(Map))).collectList().block()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == 500
        e.response.getBody(Map).get().message == 'boom'

        when:
        client.toAsyncStreaming().exchangeStream(HttpRequest.GET('/error-status/json/500')).toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        def ee = thrown(ExecutionException)
        ee.cause instanceof HttpClientResponseException
        ((HttpClientResponseException) ee.cause).response.code() == 500

        cleanup:
        client.close()
        ctx.close()
    }

    void "the response of an error exception decodes its body into the body type, and not the error type, by default"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        HttpClient client = ctx.createBean(HttpClient, server.URL)

        when:
        client.toBlocking().exchange(HttpRequest.GET('/error-status/json/400'), Argument.of(Map), Argument.of(ErrorBody))

        then:
        def e = thrown(HttpClientResponseException)
        e.message == 'boom'
        e.response.body() == [message: 'boom']

        cleanup:
        client.close()
        ctx.close()
    }

    void "the error body is decoded into the error type when jdk.decode-error-type is enabled"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.jdk.decode-error-type': true])
        HttpClient client = ctx.createBean(HttpClient, server.URL)

        when:
        client.toBlocking().exchange(HttpRequest.GET('/error-status/json/400'), Argument.of(Map), Argument.of(ErrorBody))

        then:
        def e = thrown(HttpClientResponseException)
        e.response.getBody(ErrorBody).get().message == 'boom'
        e.message.contains('ErrorBody')

        cleanup:
        client.close()
        ctx.close()
    }

    @Introspected
    static class ErrorBody {
        String message
    }

    @Requires(property = 'spec.name', value = 'JdkErrorStatusResponseSpec')
    @Controller('/error-status')
    static class ErrorStatusController {
        @Get(uri = '/{status}', produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> status(int status) {
            return HttpResponse.<String> status(io.micronaut.http.HttpStatus.valueOf(status)).body("status $status".toString())
        }

        @Get(uri = '/json/{status}', produces = MediaType.APPLICATION_JSON)
        HttpResponse<String> json(int status) {
            return HttpResponse.<String> status(io.micronaut.http.HttpStatus.valueOf(status)).body('{"message":"boom"}')
        }
    }
}
