package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.reactivestreams.Publisher
import spock.lang.Issue
import spock.lang.Specification

class ImmediateCloseSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6723')
    def 'immediate close of client connection should not lead to log message'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'ImmediateCloseSpec',
        ])

        when:
        Holder holder = embeddedServer.applicationContext.getBean(Holder) // Get holder before the close is called and instance is destroyed
        HttpGet get = new HttpGet(embeddedServer.URI.toString() + '/empty')
        CloseableHttpClient httpClient = HttpClients.createDefault()
        CloseableHttpResponse response = httpClient.execute(get)
        httpClient.close()
        embeddedServer.close() // wait for connection handling to finish

        then:
        response.getStatusLine().statusCode == 401
        holder.handleCount == 1
        // can't check for log messages :(

        cleanup:
        embeddedServer.close()
        httpClient.close()
    }

    @Requires(property = 'spec.name', value = 'ImmediateCloseSpec')
    @Controller('/empty')
    static class EmptyController {
        @Get
        HttpResponse<?> get() {
            return HttpResponse.ok()
        }
    }

    @Requires(property = 'spec.name', value = 'ImmediateCloseSpec')
    @Singleton
    static class Holder {
        int handleCount = 0
    }

    @Requires(property = 'spec.name', value = 'ImmediateCloseSpec')
    @io.micronaut.http.annotation.Filter('/**')
    static class Filter implements HttpServerFilter {
        @Inject Holder holder

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            holder.handleCount++
            return Publishers.just(HttpResponse.unauthorized())
        }
    }
}
