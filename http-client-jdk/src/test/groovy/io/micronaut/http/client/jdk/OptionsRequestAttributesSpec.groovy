package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.spockframework.util.Assert
import spock.lang.Specification

class OptionsRequestAttributesSpec extends Specification {

    def 'test OPTIONS requests attributes'() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'OptionsRequestAttributesSpec'])
        ApplicationContext ctx = server.applicationContext
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        client.toBlocking().exchange(HttpRequest.OPTIONS('/foo'), String)

        then:
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.METHOD_NOT_ALLOWED

        cleanup:
        ctx.close()
        server.close()
    }

    def 'test OPTIONS requests attributes with micronaut.server.dispatch-options-requests enabled'() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'OptionsRequestAttributesSpec', 'micronaut.server.dispatch-options-requests': StringUtils.TRUE])
        ApplicationContext ctx = server.applicationContext
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.OPTIONS('/foo'), String)

        then:
        noExceptionThrown()
        response.status == HttpStatus.NO_CONTENT
        response.getHeaders().getAll(HttpHeaders.ALLOW)
        3 == response.getHeaders().getAll(HttpHeaders.ALLOW).size()
        response.getHeaders().getAll(HttpHeaders.ALLOW).contains('GET')
        response.getHeaders().getAll(HttpHeaders.ALLOW).contains('OPTIONS')
        response.getHeaders().getAll(HttpHeaders.ALLOW).contains('HEAD')

        cleanup:
        ctx.close()
        server.close()
    }

    @Singleton
    @Controller
    @Requires(property = 'spec.name', value = 'OptionsRequestAttributesSpec')
    static class SimpleController {
        @Get('/foo')
        public String foo() {
            return "bar"
        }
    }

    @Requires(property = "spec.name", value = "OptionsRequestAttributesSpec")
    @Singleton
    @Filter("/**")
    static class MyFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            Assert.that(request.getAttributes().contains(HttpAttributes.ROUTE_MATCH.toString()))
            Assert.that(request.getAttributes().contains(HttpAttributes.ROUTE_INFO.toString()))
            Assert.that(request.getAttributes().contains(HttpAttributes.URI_TEMPLATE.toString()))
            return chain.proceed(request)
        }
    }
}
