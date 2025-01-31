package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.util.StringUtils
import io.micronaut.http.BasicHttpAttributes
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
import io.micronaut.web.router.RouteAttributes
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
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

        and: 'filter is invoked'
        MyFilter myFilter = ctx.getBean(MyFilter)
        myFilter.containsRouteInfo != null && myFilter.containsRouteMatch != null && myFilter.containsUriTemplate != null

        and: 'but no route info/match or uri tempalte information is present'
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
        String foo() {
            return "bar"
        }
    }

    @Requires(property = "spec.name", value = "OptionsRequestAttributesSpec")
    @Singleton
    @Filter("/**")
    static class MyFilter implements HttpServerFilter {
        Boolean containsRouteMatch
        Boolean containsRouteInfo
        Boolean containsUriTemplate

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            containsRouteMatch = RouteAttributes.getRouteMatch(request).isPresent()
            containsRouteInfo = RouteAttributes.getRouteInfo(request).isPresent()
            containsUriTemplate = BasicHttpAttributes.getUriTemplate(request).isPresent()
            return chain.proceed(request)
        }
    }
}
