package io.micronaut.http.server.codec

import io.micronaut.buffer.netty.NettyByteBufferFactory
import io.micronaut.context.BeanProvider
import io.micronaut.http.codec.MediaTypeCodecRegistry
import io.micronaut.http.sse.Event
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration

class TextStreamCodecSpec extends Specification {

    @Shared
    TextStreamCodec codec = new TextStreamCodec(
            StandardCharsets.UTF_8,
            NettyByteBufferFactory.DEFAULT,
            { -> null } as BeanProvider<MediaTypeCodecRegistry>,
            null)

    void "an event without data is encoded without a data field"() {
        when:
        def buffer = codec.encode(event, NettyByteBufferFactory.DEFAULT)
        def written = buffer.toString(StandardCharsets.UTF_8)
        buffer.release()

        then:
        written == expected

        where:
        event                                              | expected
        Event.<String>empty().comment('ping')              | ': ping\n\n\n'
        Event.<String>empty().id('1')                      | 'id: 1\n\n\n'
        Event.<String>empty().retry(Duration.ofSeconds(1)) | 'retry: 1000\n\n\n'
        Event.of('x')                                      | 'data: x\n\n'
    }
}
