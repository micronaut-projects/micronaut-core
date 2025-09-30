package io.micronaut.http.netty.body

import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.http.body.stream.BufferConsumer
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import reactor.core.publisher.Flux
import spock.lang.Specification

class NettyBodyAdapterSpec extends Specification {
    def 'empty buffers'() {
        given:
        def flux = Flux.just(Unpooled.EMPTY_BUFFER, Unpooled.wrappedBuffer(new byte[] {1, 2, 3}))
        def adapter = new NettyByteBodyFactory(new EmbeddedChannel()).adaptNetty(flux)
        def received = Unpooled.buffer()
        def upstream = adapter.primary(new BufferConsumer() {
            @Override
            void add(ReadBuffer buf) {
                def bb = NettyReadBufferFactory.toByteBuf(buf)
                received.writeBytes(bb)
                bb.release()
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
