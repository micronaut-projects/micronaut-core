package io.micronaut.http.client.convert

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class TypeConverterWithMethodReferenceSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'TypeConverterWithMethodReferenceSpec',
    ])

    @Shared
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test type converters can be used with method references"() {
        when:
        def foo = client.toBlocking().retrieve('/convert/foo/val', Foo)
        def bar = client.toBlocking().retrieve('/convert/bar/val', Bar)

        then:
        foo.value == 'val'
        bar.value == 'val'
    }

    @Controller("/convert")
    @Requires(property = "spec.name", value = 'TypeConverterWithMethodReferenceSpec')
    static class FooBarController {

        @Get("/foo/{foo}")
        Foo foo(@PathVariable(name = "foo") Foo foo) {
            return foo;
        }

        @Get("/bar/{bar}")
        Bar bar(@PathVariable(name = "bar") Bar bar) {
            return bar;
        }
    }
}
