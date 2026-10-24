package io.micronaut.http.client.aop

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ClientHeaderPlaceholderSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ClientHeaderPlaceholderSpec'])

    void "a method header whose placeholder resolves to a new value is resolved for every request"() {
        given:
        HeaderPlaceholderClient client = server.applicationContext.getBean(HeaderPlaceholderClient)

        when:
        def first = client.id()
        def second = client.id()

        then:
        first
        second
        first != second
    }

    @Requires(property = 'spec.name', value = 'ClientHeaderPlaceholderSpec')
    @Client('/client-header-placeholder')
    static interface HeaderPlaceholderClient {
        @Get('/id')
        @Header(name = 'X-Request-Id', value = '${random.uuid}')
        String id()
    }

    @Requires(property = 'spec.name', value = 'ClientHeaderPlaceholderSpec')
    @Controller('/client-header-placeholder')
    static class HeaderController {
        @Get('/id')
        String id(@Header('X-Request-Id') String id) {
            id
        }
    }
}
