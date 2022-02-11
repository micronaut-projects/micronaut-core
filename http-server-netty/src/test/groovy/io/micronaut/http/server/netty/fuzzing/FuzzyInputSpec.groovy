package io.micronaut.http.server.netty.fuzzing

import io.micronaut.context.ApplicationContext
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.resolver.NoopAddressResolverGroup
import spock.lang.Specification

/**
 * HTTP inputs generated from fuzzing.
 */
class FuzzyInputSpec extends Specification {

    def 'http1 cleartext buffer leaks'() {
        given:
        BufferLeakDetection.startTracking()

        ApplicationContext ctx = ApplicationContext.run([
                "micronaut.server.port": "-1",
                "micronaut.netty.event-loops.default.num-threads": "1"
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        def clientBootstrap = new Bootstrap()
                .remoteAddress(new InetSocketAddress(embeddedServer.getHost(),
                        embeddedServer.getPort()))
                .resolver(NoopAddressResolverGroup.INSTANCE)
                .group(ctx.getBean(EventLoopGroupRegistry.class).getEventLoopGroup(EventLoopGroupConfiguration.DEFAULT).get())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new ReadTimeoutHandler(1))
                    }
                })

        when:
        def channel = clientBootstrap.connect().sync().channel()
        channel.writeAndFlush(Unpooled.wrappedBuffer(input))
        channel.closeFuture().sync()

        then:
        BufferLeakDetection.stopTrackingAndReportLeaks()

        cleanup:
        embeddedServer.stop()

        where:
        input << [
                Base64.decoder.decode("T1BUSU9OUyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKiBIVFRQLzEuMQpUcmFuc2Zlci1FbmNvZGluZzpjaHVua2VkCgo0CgoNSU9OUyAqIEhUVFAvMS4xClRyYW5zZmVyLUVuY29kaW5nOmNodW5rZWSKCg")
        ]
    }
}
