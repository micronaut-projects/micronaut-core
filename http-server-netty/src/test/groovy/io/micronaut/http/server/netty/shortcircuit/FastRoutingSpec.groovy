package io.micronaut.http.server.netty.shortcircuit

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class FastRoutingSpec extends Specification {
    def test(boolean fastRouting) {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'FastRoutingSpec', 'micronaut.server.netty.optimized-routing': fastRouting])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        expect:
        // if this returns the wrong boolean value, then fast routing is broken for this route. maybe you added a filter that applies to this test?
        client.retrieve("/simple") == "foo: ${!fastRouting}"

        client.retrieve(HttpRequest.POST("/json", '{"foo":  "bar"}')) == '{"foo":"bar"}'

        cleanup:
        server.stop()
        client.close()
        ctx.close()

        where:
        fastRouting << [false, true]
    }

    @Controller
    @Requires(property = "spec.name", value = "FastRoutingSpec")
    static class MyController {
        @Get("/simple")
        String simple() {
            return "foo: " + ServerRequestContext.currentRequest().isPresent()
        }

        @Post("/json")
        MyRecord json(@Body MyRecord json) {
            return json
        }
    }

    @Introspected
    record MyRecord(String foo) {}
}
