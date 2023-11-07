package io.micronaut.http.client.convert

import io.micronaut.context.ApplicationContext
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
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test type converters can be used with method references"() {
        when:
        def foo = client.toBlocking().retrieve('/convert/foo/val', Foo)
        def bar = client.toBlocking().retrieve('/convert/bar/val', Bar)

        then:
        foo.value == 'val'
        bar.value == 'val'
    }
}
