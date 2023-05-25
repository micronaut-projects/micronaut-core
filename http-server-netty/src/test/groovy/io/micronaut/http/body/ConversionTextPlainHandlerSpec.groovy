package io.micronaut.http.body

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.time.Instant

class ConversionTextPlainHandlerSpec extends Specification {
    def test() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'ConversionTextPlainHandlerSpec',
                'micronaut.http.legacy-text-conversion': true
        ])
        def val = Instant.now()
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.getBean(MyClient)

        expect:
        client.test(val) == val

        cleanup:
        server.stop()
        ctx.close()
    }

    @Client("/conv-text-plain")
    @Requires(property = "spec.name", value = "ConversionTextPlainHandlerSpec")
    static interface MyClient {
        @Post
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.TEXT_PLAIN)
        Instant test(@Body Instant instant)
    }

    @Controller("/conv-text-plain")
    @Requires(property = "spec.name", value = "ConversionTextPlainHandlerSpec")
    static class MyController {
        @Post
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.TEXT_PLAIN)
        Instant test(@Body Instant instant) {
            return instant
        }
    }
}
