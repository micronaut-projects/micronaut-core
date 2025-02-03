package io.micronaut.http.netty.body

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import reactor.core.publisher.Flux
import spock.lang.Specification

class NettyBodyAdapterSpec extends Specification {
    def 'empty buffers'() {
        given:
        def flux = Flux.just(Unpooled.EMPTY_BUFFER, Unpooled.wrappedBuffer(new byte[] {1, 2, 3}))
        def adapter = NettyBodyAdapter.adapt(flux, new EmbeddedChannel().eventLoop())
        def received = Unpooled.buffer()
        def upstream = adapter.primary(new ByteBufConsumer() {
            @Override
            void add(ByteBuf buf) {
                received.writeBytes(buf)
                buf.release()
            }

            @Override
            void complete() {
            }

            @Override
            void error(Throwable e) {
            }
        })

        expect:
        !received.isReadable()

        when:
        upstream.start()
        then:
        received.isReadable()
        received.readByte() == (byte) 1
    }
}
