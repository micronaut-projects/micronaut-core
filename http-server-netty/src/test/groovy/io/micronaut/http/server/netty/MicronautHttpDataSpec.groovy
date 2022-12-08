package io.micronaut.http.server.netty

import io.micronaut.http.server.HttpServerConfiguration
import io.netty.buffer.Unpooled
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class MicronautHttpDataSpec extends Specification {
    def 'add to chunk'(def threshold) {
        given:
        def cfg = new HttpServerConfiguration.MultipartConfiguration()
        cfg.mixed = true
        cfg.threshold = threshold
        def data = new MicronautHttpData.Factory(cfg, StandardCharsets.UTF_8).createAttribute("")

        when:
        data.addContent(Unpooled.wrappedBuffer("foo".bytes), false)
        data.addContent(Unpooled.wrappedBuffer("bar".bytes), true)
        def chunk1 = data.pollChunk()
        then:
        chunk1.claim().toString(StandardCharsets.UTF_8) == "foobar"

        cleanup:
        data.release()

        where:
        threshold << [0, 4, 1000]
    }
}
