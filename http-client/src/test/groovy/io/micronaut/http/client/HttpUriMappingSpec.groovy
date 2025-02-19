package io.micronaut.http.client

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Specification

@MicronautTest
@Property(name = 'spec.name', value = 'HttpUriMappingSpec')
class HttpUriMappingSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client;

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10957")
    void "test a path with default uri mapping and a path with uris custom"() {
        when:
        def root = client.toBlocking().exchange("/")
        def foo = client.toBlocking().exchange("/foo")
        def bar = client.toBlocking().exchange("/bar")
        def baz = client.toBlocking().exchange("/baz")

        then:
        root.status() == HttpStatus.OK
        foo.status() == HttpStatus.OK
        bar.status() == HttpStatus.OK
        baz.status() == HttpStatus.OK
        root.getBody(String).get() == "root"
        foo.getBody(String).get() == "foo"
        bar.getBody(String).get() == "bar"
        baz.getBody(String).get() == "bar"
    }

    @Controller
    @Requires(property = 'spec.name', value = 'HttpUriMappingSpec')
    static class AnyController {

        @Get
        String root() {
            return "root";
        }

        @Get(uris = ["/foo"])
        String foo() {
            return "foo";
        }

        @Get(uri = "baz", uris = ["/bar"])
        String bar() {
            return "bar";
        }
    }
}
