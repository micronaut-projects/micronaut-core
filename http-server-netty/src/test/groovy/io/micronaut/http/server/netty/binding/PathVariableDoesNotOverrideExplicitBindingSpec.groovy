package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.client.HttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PathVariableDoesNotOverrideExplicitBindingSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'PathVariableDoesNotOverrideExplicitBindingSpec'])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a request attribute named like a path variable is not replaced by it"() {
        expect:
        client.toBlocking().retrieve('/explicit-binding/ta/victim-tenant') == 'attr=server-tenant'
    }

    void "a header named like a path variable is not replaced by it"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/explicit-binding/ha/P').header('tenant', 'H')) == 'header=H'
    }

    void "a cookie named like a path variable is not replaced by it"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/explicit-binding/ca/P').cookie(Cookie.of('tenant', 'K'))) == 'cookie=K'
    }

    @Requires(property = 'spec.name', value = 'PathVariableDoesNotOverrideExplicitBindingSpec')
    @Filter('/explicit-binding/**')
    static class TenantFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            request.setAttribute('tenant', 'server-tenant')
            return chain.proceed(request)
        }
    }

    @Requires(property = 'spec.name', value = 'PathVariableDoesNotOverrideExplicitBindingSpec')
    @Controller('/explicit-binding')
    static class Ctrl {
        @Get('/ta/{tenant}')
        String attrOnly(@RequestAttribute String tenant) {
            "attr=$tenant"
        }

        @Get('/ha/{tenant}')
        String headerOnly(@Header String tenant) {
            "header=$tenant"
        }

        @Get('/ca/{tenant}')
        String cookieOnly(@CookieValue String tenant) {
            "cookie=$tenant"
        }
    }
}
