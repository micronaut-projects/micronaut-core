package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class NettyClientHttpRequestSpec extends Specification {

    void "test getParameters"() {
        when:
        MutableHttpRequest req = new NettyClientHttpRequestFactory().get("/foo?param=true")

        then:
        req.getParameters().get("param", Boolean)
    }

    void "test mutating params mutates the URI"() {
        when:
        MutableHttpRequest req = new NettyClientHttpRequestFactory().get("/foo?param=true")
        req.getParameters().add("param", "false")

        then:
        req.getParameters().get("param", Argument.listOf(Boolean)).get() == [true, false]
        req.uri.toString() == "/foo?param=true&param=false"
    }

    void "test combination of URI params and request params"() {
        MutableHttpRequest req = new NettyClientHttpRequestFactory().get("/test-params?param=true")
        req.getParameters().add("param", "false")

        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': NettyClientHttpRequestSpec.simpleName])
        ApplicationContext ctx = server.applicationContext
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        List<Boolean> response = client.toBlocking().retrieve(req, Argument.listOf(Boolean))

        then:
        response == [true, false]
    }

    @Requires(property = "spec.name", value = "NettyClientHttpRequestSpec")
    @Controller("/test-params")
    static class TestController {

        @Get
        List<Boolean> params(@QueryValue List<Boolean> param) {
            return param
        }
    }
}
