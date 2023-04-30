package io.micronaut.http.hateoas

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Specification

import jakarta.validation.constraints.Pattern

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/2863")
@Property(name = "spec.name", value = "JsonErrorEmbeddedSpec")
@MicronautTest
class JsonErrorEmbeddedSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient

    void "JsonError returns embedded"() {
        when:
        HttpResponse<String> response = httpClient.toBlocking().exchange('/say/hello/sergio', String, JsonError)

        then:
        noExceptionThrown()
        HttpStatus.OK == response.status()
        "Hello sergio" == response.body()

        when:
        httpClient.toBlocking().exchange('/say/hello/john', String, JsonError)

        then:
        HttpClientResponseException e = thrown()
        HttpStatus.BAD_REQUEST == e.response.status()
        Optional<JsonError> errorOptional = e.response.getBody(JsonError)

        then:
        errorOptional.isPresent()

        when:
        JsonError error = errorOptional.get()

        then:
        error
        !error.embedded.isEmpty()
    }

    @Requires(property = "spec.name", value = "JsonErrorEmbeddedSpec")
    @Controller("/say")
    static class EchoNameController {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/hello/{name}")
        String hello(@PathVariable @Pattern(regexp = "sergio|tim") String name) {
            return "Hello " + name
        }
    }

}
