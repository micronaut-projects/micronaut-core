package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PathVariableDoesNotOverrideExplicitBindingSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'PathVariableDoesNotOverrideExplicitBindingSpec'])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a request attribute is not replaced by a path variable of the same name"() {
        expect:
        client.toBlocking().retrieve('/explicit-binding/t/victim-tenant/orders') == 'path=victim-tenant attr=server-tenant'
    }

    void "a header is not replaced by a path variable of the same name"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/explicit-binding/h/P').header('tenant', 'H')) == 'path=P header=H'
    }

    void "a cookie is not replaced by a path variable of the same name"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/explicit-binding/c/P').cookie(Cookie.of('tenant', 'K'))) == 'path=P cookie=K'
    }

    @Requires(property = 'spec.name', value = 'PathVariableDoesNotOverrideExplicitBindingSpec')
    @ServerFilter('/explicit-binding/**')
    static class TenantFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            request.setAttribute('tenant', 'server-tenant')
        }
    }

    @Requires(property = 'spec.name', value = 'PathVariableDoesNotOverrideExplicitBindingSpec')
    @Controller('/explicit-binding')
    static class Ctrl {
        @Get('/t/{tenant}/orders')
        String orders(@PathVariable('tenant') String tenant, @RequestAttribute('tenant') String authTenant) {
            "path=$tenant attr=$authTenant"
        }

        @Get('/h/{tenant}')
        String header(@PathVariable('tenant') String tenant, @Header('tenant') String header) {
            "path=$tenant header=$header"
        }

        @Get('/c/{tenant}')
        String cookie(@PathVariable('tenant') String tenant, @CookieValue('tenant') String cookie) {
            "path=$tenant cookie=$cookie"
        }
    }
}
