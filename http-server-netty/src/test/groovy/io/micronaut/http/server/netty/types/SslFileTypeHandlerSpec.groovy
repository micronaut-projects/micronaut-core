package io.micronaut.http.server.netty.types

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.AbstractMicronautSpec

class SslFileTypeHandlerSpec extends AbstractMicronautSpec {

    private static File tempFile

    static {
        tempFile = File.createTempFile("sslFileTypeHandlerSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page</body></html>")
        tempFile
    }

    void "test returning a file from a controller"() {
        when:
        def response = rxClient.exchange('/test/html', String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "<html><head></head><body>HTML Page</body></html>"
    }

    Map<String, Object> getConfiguration() {
        super.getConfiguration() << ['micronaut.ssl.enabled': true, 'micronaut.ssl.buildSelfSigned': true]
    }

    @Controller
    @Requires(property = 'spec.name', value = 'SslFileTypeHandlerSpec')
    static class TestController {

        @Get
        File html() {
            tempFile
        }
    }
}
