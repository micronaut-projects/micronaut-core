package io.micronaut.http.server.netty.interceptor

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux

class HttpFilterContextPathSpec extends AbstractMicronautSpec {

    @Override
    Map<String, Object> getConfiguration() {
        super.getConfiguration() << ['micronaut.server.context-path': '/context/path']
    }

    void "test interceptor execution and order - proceed"() {
        when:
        HttpResponse<String> response = rxClient.toBlocking().exchange("/context/path/secure?username=fred", String)

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Test") == "Foo Test"
        response.body.isPresent()
        response.body.get() == "Authenticated: fred"
    }

    void "test a filter on the root url"() {
        when:
        HttpResponse response = rxClient.toBlocking().exchange("/context/path")

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Context-Path") == "true"
    }

    @Requires(property = 'spec.name', value = "HttpFilterContextPathSpec")
    @Controller
    static class RootController {

        @Get
        HttpResponse root() {
            HttpResponse.ok()
        }

    }
}
