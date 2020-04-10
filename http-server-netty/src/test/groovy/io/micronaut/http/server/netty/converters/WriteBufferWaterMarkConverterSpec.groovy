package io.micronaut.http.server.netty.converters

import io.micronaut.context.annotation.Property
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = 'micronaut.server.netty.childOptions.write_buffer_water_mark.high', value = '262143')
@Property(name = 'micronaut.server.netty.childOptions.write_buffer_water_mark.low', value = '65535')
class WriteBufferWaterMarkConverterSpec extends Specification {

    @Inject
    EmbeddedServer embeddedServer

    @Inject
    NettyHttpServerConfiguration httpServerConfiguration

    void "test WriteBufferWaterMark is correct"() {
        given:
        def options = httpServerConfiguration.getChildOptions()
        ServerBootstrap bootstrap = new ServerBootstrap()
        embeddedServer.processOptions(null, options, { option, val -> bootstrap.option(option, val)})
        def writeBufferWaterMark = bootstrap.config().options().get(ChannelOption.WRITE_BUFFER_WATER_MARK)
        expect:
        writeBufferWaterMark instanceof WriteBufferWaterMark
        writeBufferWaterMark.high == 262143
        writeBufferWaterMark.low == 65535

    }
}
