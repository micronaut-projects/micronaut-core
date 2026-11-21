package io.micronaut.http.server.netty.path

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Unroll

/**
 * exact and single-segment-wildcard filters are bypassed with a trailing slash.
 */
class FilterTrailingSlashSpec extends Specification {

    @Unroll
    def 'filter guards #path'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterTrailingSlashSpec'])

        when:
        def response = RawHttp.get(server.port, path)

        then:
        RawHttp.statusLine(response).contains('403')

        cleanup:
        server?.close()

        where:
        path << ['/admin', '/admin/', '/item/1', '/item/1/']
    }

    @Requires(property = 'spec.name', value = 'FilterTrailingSlashSpec')
    @ServerFilter(['/admin', '/item/*'])
    static class Guard {
        @RequestFilter
        HttpResponse<?> filter(HttpRequest<?> request) { HttpResponse.status(HttpStatus.FORBIDDEN) }
    }

    @Requires(property = 'spec.name', value = 'FilterTrailingSlashSpec')
    @Controller
    static class Ctrl {
        @Get('/admin') String admin() { 'ADMIN-BODY' }
        @Get('/item/{id}') String item(String id) { 'ITEM-' + id }
    }
}
