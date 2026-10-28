package io.micronaut.http.server.netty.path

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * path-scoped filters match the raw path, static files resolve from the decoded path.
 */
class StaticResourceFilterPathSpec extends Specification {

    @Shared Path dir = Files.createTempDirectory('static-filter-path')

    def setupSpec() {
        Files.writeString(dir.resolve('data.txt'), 'PROTECTED-CONTENT')
    }

    @Unroll
    def 'the /priv/** guard applies to #path'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'StaticResourceFilterPathSpec',
            'micronaut.router.static-resources.p.paths': 'file:' + dir.toString(),
            'micronaut.router.static-resources.p.mapping': '/priv/**'])

        when:
        def response = RawHttp.get(server.port, path)

        then:
        !response.contains('PROTECTED-CONTENT')

        cleanup:
        server?.close()

        where:
        path << ['/priv/data.txt', '/%70riv/data.txt', '/priv%2Fdata.txt', '//x.invalid/priv/data.txt']
    }

    @Requires(property = 'spec.name', value = 'StaticResourceFilterPathSpec')
    @ServerFilter('/priv/**')
    static class Guard {
        @RequestFilter
        HttpResponse<?> filter(HttpRequest<?> request) { HttpResponse.status(HttpStatus.FORBIDDEN).body('guard-denied') }
    }
}
