package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpHeaders
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class NettyClientHttpRequestSpec extends Specification {

    void "test getParameters"() {
        when:
        MutableHttpRequest req = new NettyClientHttpRequestFactory().get("/foo?param=true")

        then:
        req.getParameters().get("param", Boolean)
    }

    void "cookies added together are separated as cookies added one by one"() {
        when:
        MutableHttpRequest req = new NettyClientHttpRequestFactory().get("/foo")
        req.cookies([Cookie.of("a", "1"), Cookie.of("b", "2")] as LinkedHashSet)

        then:
        req.headers.getAll(HttpHeaders.COOKIE) == ["a=1; b=2"]
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
