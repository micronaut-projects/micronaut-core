package io.micronaut.http.client.jdk

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "RawStringHandlingSpec")
class RawStringHandlingSpec extends Specification {

    @Inject
    RawClient client

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/9223")
    void "test raw return type handling"() {
        when:
        def result = client.string()

        then:
        result.get() == "Hello World"

        when:
        result = client.noString()

        then:
        result.empty

        when:
        result = client.optional('present')

        then:
        result.get() == "Hello World"

        when:
        result = client.optional('absent')

        then:
        result.empty
    }

    @Controller("/raw")
    @Requires(property = "spec.name", value = "RawStringHandlingSpec")
    static class RawController {

        @Get(value = "/string")
        String string() {
            "Hello World"
        }

        @Get(value = "/no-string")
        String noString() {
            null
        }

        @Get(value = "/optional")
        Optional<String> optional(@QueryValue String name) {
            if (name == 'present') {
                return Optional.of("Hello World")
            } else {
                return Optional.empty()
            }
        }
    }

    @Client("/")
    @Requires(property = "spec.name", value = "RawStringHandlingSpec")
    static interface RawClient {

        @Get("/raw/string")
        Optional<String> string()

        @Get("/raw/no-string")
        Optional<String> noString()

        @Get("/raw/optional")
        Optional<String> optional(@QueryValue String name)
    }
}
