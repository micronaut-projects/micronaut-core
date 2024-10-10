package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
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

        and:
        MyFilter myFilter = ctx.getBean(MyFilter)
        !myFilter.containsRouteInfo
        !myFilter.containsRouteMatch
        !myFilter.containsUriTemplate

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
        response.status == HttpStatus.OK

        when:
        List<String> allowedMethods = response.getHeaders().get(HttpHeaders.ALLOW, Argument.of(List.class, Argument.of(String.class))).orElse(new ArrayList<>())

        then:
        3 == allowedMethods.size()
        allowedMethods.contains('GET')
        allowedMethods.contains('OPTIONS')
        allowedMethods.contains('HEAD')

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
        boolean containsRouteMatch = false
        boolean containsRouteInfo = false
        boolean containsUriTemplate = false

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            containsRouteMatch = request.getAttributes().contains(HttpAttributes.ROUTE_MATCH.toString())
            containsRouteInfo = request.getAttributes().contains(HttpAttributes.ROUTE_INFO.toString())
            containsUriTemplate = request.getAttributes().contains(HttpAttributes.URI_TEMPLATE.toString())
            return chain.proceed(request)
        }
    }
}
