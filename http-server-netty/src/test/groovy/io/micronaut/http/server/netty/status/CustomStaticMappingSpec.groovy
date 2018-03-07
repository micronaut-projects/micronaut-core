package io.micronaut.http.server.netty.status

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post

/**
 * Created by graemerocher on 13/09/2017.
 */
class CustomStaticMappingSpec extends AbstractMicronautSpec {

    void "test that a bad request response can be redirected by the router"() {
        when:
        rxClient.exchange('/test/bad').blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff"

    }

    void "test that a bad request response for invalid request data can be redirected by the router"() {
        when:
        rxClient.exchange(
                HttpRequest.POST('/test/simple', [name:"Fred"])
                           .contentType(MediaType.FORM)
        ).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff"

    }

    @Controller
    @Requires(property = 'spec.name', value = 'CustomStaticMappingSpec')
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED )
    static class TestController {
        @Get
        HttpResponse bad() {
            HttpResponse.badRequest()
        }

        @Post
        String simple(String name, Integer age) {
            "name: $name, age: $age"
        }

        @Error(status = HttpStatus.BAD_REQUEST)
        HttpResponse badHandler() {
            HttpResponse.status(HttpStatus.BAD_REQUEST, "You sent me bad stuff")
        }
    }
}
