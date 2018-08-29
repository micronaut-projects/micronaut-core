package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Get
import io.micronaut.http.HttpRequest

class RegexBindingSpec extends AbstractMicronautSpec {

    void "test regex matches"() {
        when:
        HttpResponse response = rxClient.toBlocking().exchange(HttpRequest.GET("/test-binding/regex/blue"))

        then:
        response.status() == HttpStatus.OK
    }

    void "test regex does not match"() {
        when:
        rxClient.toBlocking().exchange(HttpRequest.GET("/test-binding/regex/yellow"))

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.NOT_FOUND
    }

    @Controller('/test-binding')
    @Requires(property = 'spec.name', value = 'RegexBindingSpec')
    static class TestController {

        @Get('/regex/{color:^blue|orange$}')
        HttpStatus regex(String color) {
            HttpStatus.OK
        }
    }
}
