package io.micronaut.function.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.function.FunctionBean
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.net.http.HttpClient
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * The HEAD route of a function runs the function like GET does, so GET-scoped filters apply to it.
 */
class HeadFunctionFilterSpec extends Specification {

    void "a GET-scoped filter guards the HEAD route of a supplier function"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'HeadFunctionFilterSpec'])
        HttpClient client = HttpClient.newHttpClient()
        def uri = URI.create("http://localhost:${server.port}/head-secret")
        SecretFunction.CALLS.set(0)

        when:
        def get = client.send(java.net.http.HttpRequest.newBuilder(uri).build(), java.net.http.HttpResponse.BodyHandlers.discarding())
        def head = client.send(java.net.http.HttpRequest.newBuilder(uri).method('HEAD', java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding())

        then:
        get.statusCode() == 403
        head.statusCode() == 403
        SecretFunction.CALLS.get() == 0

        cleanup:
        client?.close()
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'HeadFunctionFilterSpec')
    @FunctionBean("head-secret")
    static class SecretFunction implements Supplier<String> {
        static final AtomicInteger CALLS = new AtomicInteger()

        @Override
        String get() {
            CALLS.incrementAndGet()
            return 'secret-value'
        }
    }

    @Requires(property = 'spec.name', value = 'HeadFunctionFilterSpec')
    @ServerFilter(value = '/head-secret/**', methods = HttpMethod.GET)
    static class GetOnlyFilter {
        @RequestFilter
        HttpResponse<?> deny() {
            return HttpResponse.status(HttpStatus.FORBIDDEN)
        }
    }
}
