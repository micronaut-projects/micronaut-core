package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class NonMutableResponseSpec extends Specification {

    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.max-request-size': '10KB',
            'spec.name'                        : 'NonMutableResponseSpec'
    ])

    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test returning a non mutable response from a controller"() {
        expect:
        client.toBlocking().retrieve('/test/non-mutable') == 'test'
        client.toBlocking().retrieve('/test/non-mutable/single') == 'test'
        client.toBlocking().retrieve('/test/non-mutable/completable') == 'test'
        client.toBlocking().retrieve('/test/non-mutable/publisher') == 'test'
    }

    @Requires(property = 'spec.name', value = 'NonMutableResponseSpec')
    @Client('/')
    static interface ResponseClient {

        @Get('/test/non-mutable/proxy')
        HttpResponse<String> go()

        @Get('/test/non-mutable/proxy')
        CompletableFuture<HttpResponse<String>> goCompletable()

        @Get('/test/non-mutable/proxy')
        @SingleResult
        Publisher<HttpResponse<String>> goMono()

        @Get('/test/non-mutable/proxy')
        Publisher<HttpResponse<String>> goPublisher()
    }

    @Requires(property = 'spec.name', value = 'NonMutableResponseSpec')
    @Controller
    static class ResponseController {

        @Inject
        ResponseClient responseClient

        @Get('/test/non-mutable')
        @ExecuteOn(TaskExecutors.BLOCKING)
        HttpResponse<String> go() {
            responseClient.go()
        }

        @Get('/test/non-mutable/completable')
        CompletableFuture<HttpResponse<String>> goCompletable() {
            responseClient.goCompletable()
        }

        @Get('/test/non-mutable/single')
        @SingleResult
        Publisher<HttpResponse<String>> goMono() {
            responseClient.goMono()
        }

        @Get('/test/non-mutable/publisher')
        Publisher<HttpResponse<String>> goPublisher() {
            responseClient.goPublisher()
        }

        @Get('/test/non-mutable/proxy')
        HttpResponse<String> proxy() {
            HttpResponse.ok("test")
        }
    }
}
