package io.micronaut.http.client.jdk

import groovy.transform.Canonical
import groovy.transform.ToString
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import spock.lang.Specification

class HttpProxySpec extends Specification {

    def "test http proxy"() {
        given: 'a proxy server'
        EmbeddedServer proxy = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'HttpProxySpec_Proxy'
        ])

        and: 'a target server configured to use the proxy'
        EmbeddedServer target = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'HttpProxySpec',
                'micronaut.http.client.proxy-address': "localhost:${proxy.getURI().port}",
        ])

        and: 'the proxy knows where the target server is'
        proxy.applicationContext.registerSingleton(new TargetPort(target.getPort()))

        and: 'a client for the target app'
        def client = target.applicationContext.createBean(HttpClient, target.URL)

        when: 'we make a request'
        def response = client.toBlocking().retrieve("/test/1")

        then: 'the response is proxied'
        response == "Proxied hello"

        cleanup:
        proxy.stop()
        target.stop()
    }

    @Controller("/test/{id}")
    @Requires(property = "spec.name", value = "HttpProxySpec")
    static class MyController {

        @Get
        @Produces("text/plain")
        String get() {
            "hello"
        }
    }

    @Controller("/{whatever}/{id}")
    @Requires(property = "spec.name", value = "HttpProxySpec_Proxy")
    static class MyProxy {

        final BeanContext ctx

        MyProxy(BeanContext ctx) {
            this.ctx = ctx
        }

        @Get
        @ExecuteOn(TaskExecutors.BLOCKING)
        String get(HttpRequest<?> request) {
            "Proxied " + ctx.createBean(HttpClient, "http://localhost:${ctx.getBean(TargetPort).port}".toURI())
                    .toBlocking()
                    .retrieve(request.getUri().toASCIIString())
        }
    }

    @Canonical
    @ToString
    static class TargetPort {
        int port
    }
}
