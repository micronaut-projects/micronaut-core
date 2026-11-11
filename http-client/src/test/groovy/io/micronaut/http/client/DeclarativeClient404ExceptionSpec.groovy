package io.micronaut.http.client

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@MicronautTest
@Property(name = 'spec.name', value = 'DeclarativeClient404ExceptionSpec')
class DeclarativeClient404ExceptionSpec extends Specification {

    @Inject
    ThrowOn404Client client

    @Controller('/test404')
    @Requires(property = 'spec.name', value = 'DeclarativeClient404ExceptionSpec')
    static class TestController {
        @Get('/notfound')
        HttpResponse<String> notFound() {
            return HttpResponse.notFound().body("Not found")
        }
    }

    @Requires(property = 'spec.name', value = 'DeclarativeClient404ExceptionSpec')
    @ConfigurationProperties('test.get_config')
    static class ThrowOn404Config extends DefaultHttpClientConfiguration {
        ThrowOn404Config() {
            setExceptionOn404Status(true)
        }
    }

    @Client(value = "/test404", configuration = ThrowOn404Config.class)
    @Requires(property = 'spec.name', value = 'DeclarativeClient404ExceptionSpec')
    static interface ThrowOn404Client {
        @Get('/notfound')
        String getSynchronous()

        @Get('/notfound')
        CompletableFuture<String> getAsync()

        @Get('/notfound')
        Mono<String> getReactive()
    }

    void "test synchronous 404 throws exception when exception-on-404-status is true"() {
        when:
        client.getSynchronous()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND
    }

    void "test async 404 throws exception when exception-on-404-status is true"() {
        when:
        client.getAsync().get()

        then:
        def ex = thrown(ExecutionException)
        ex.getCause() instanceof HttpClientResponseException
        ex.getCause().getMessage() == "Client '/test404': Not Found"
    }

    void "test reactive 404 throws exception when exception-on-404-status is true"() {
        when:
        client.getReactive().block()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.NOT_FOUND
    }
}
