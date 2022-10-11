package io.micronaut.http.cookie

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Unroll

@MicronautTest
@Property(name = "spec.name", value = "SameSiteJsonValueSpec")
class SameSiteJsonValueSpec extends Specification {
    @Inject
    @Client("/")
    HttpClient httpClient

    @Unroll
    void "SameSite is render as Json value with Lax None and Strict"(String path) {
        when:
        Map<String, Object> json = httpClient.toBlocking().retrieve(HttpRequest.GET(path), Map)

        then:
        noExceptionThrown()
        expected == json.samesite

        where:
        path                || expected
        '/samesite/lax'     || 'Lax'
        '/samesite/strict'  || 'Strict'
        '/samesite/none'    || 'None'
    }

    @Requires(property = "spec.name", value = "SameSiteJsonValueSpec")
    @Controller("/samesite")
    static class SameSiteController {

        @Get("/lax")
        Map<String, Object> lax() {
            Collections.singletonMap("samesite", SameSite.Lax)
        }

        @Get("/strict")
        Map<String, Object> strict() {
            Collections.singletonMap("samesite", SameSite.Strict)
        }

        @Get("/none")
        Map<String, Object> none() {
            Collections.singletonMap("samesite", SameSite.None)
        }
    }
}
