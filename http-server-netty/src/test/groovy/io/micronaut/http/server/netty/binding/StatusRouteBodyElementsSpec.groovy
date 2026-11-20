package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.body.BodyElements
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class StatusRouteBodyElementsSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'StatusRouteBodyElementsSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URI)

    void "the body elements of a response a status route replaces are closed"() {
        given:
        def controller = server.applicationContext.getBean(ElementsController)

        when:
        client.toBlocking().retrieve(HttpRequest.GET('/status-elements'), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
        e.response.getBody(String).orElse(null) == 'handled'

        and:
        controller.closed.get(10, TimeUnit.SECONDS) == null
    }

    @Requires(property = 'spec.name', value = 'StatusRouteBodyElementsSpec')
    @Controller('/status-elements')
    @Produces(MediaType.TEXT_PLAIN)
    static class ElementsController {
        final CompletableFuture<Void> closed = new CompletableFuture<>()

        @Get
        HttpResponse<?> get() {
            return HttpResponse.notFound(BodyElements.of(
                    () -> CompletableFuture.completedFuture(Optional.of('never')),
                    () -> closed.complete(null)))
        }

        @Error(status = HttpStatus.NOT_FOUND)
        HttpResponse<String> notFound() {
            return HttpResponse.notFound('handled')
        }
    }
}
