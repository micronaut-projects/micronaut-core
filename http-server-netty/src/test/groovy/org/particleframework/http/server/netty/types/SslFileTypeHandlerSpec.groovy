package org.particleframework.http.server.netty.types

import org.particleframework.context.annotation.Requires
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.server.netty.AbstractParticleSpec

class SslFileTypeHandlerSpec extends AbstractParticleSpec {

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
        super.getConfiguration() << ['particle.ssl.enabled': true, 'particle.ssl.buildSelfSigned': true]
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
