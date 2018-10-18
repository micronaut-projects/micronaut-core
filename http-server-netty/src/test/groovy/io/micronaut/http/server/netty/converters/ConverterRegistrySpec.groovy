package io.micronaut.http.server.netty.converters

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ConverterRegistrySpec extends Specification {

    def "test convert bytebufs"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer(2)
        compositeByteBuf.addComponent(true, Unpooled.copiedBuffer("test1", StandardCharsets.UTF_8))
        compositeByteBuf.addComponent(true, Unpooled.copiedBuffer("test2", StandardCharsets.UTF_8))


        expect:
        ctx.getBean(ConversionService).convert(compositeByteBuf, String).get() == 'test1test2'
        ctx.getBean(ConversionService).convert(Unpooled.copiedBuffer("test1", StandardCharsets.UTF_8), String).get() == 'test1'


        cleanup:
        compositeByteBuf.release()
        ctx.close()
    }
}
