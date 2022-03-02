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
                Base64.decoder.decode("T1BUSU9OUyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKiBIVFRQLzEuMQpUcmFuc2Zlci1FbmNvZGluZzpjaHVua2VkCgo0CgoNSU9OUyAqIEhUVFAvMS4xClRyYW5zZmVyLUVuY29kaW5nOmNodW5rZWSKCg"),
                Base64.decoder.decode("T0dUSU9OUyAqIEhgVFRQLzEuMQpjb250ZW50LWxlbmd2aDo0Cgqfn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fn5+fnw1JT05TIC8gSFRUUC8xLjEKVHJhbnNmZXItYG5jb2Rpbmc6Y2h1T1BUSU9OUyAqIEhUVFDU1C8xLjEKY29udGVudC1sZXZoZ246NAoKDUlPTlMgLyBIVFRQLzEuMQpUcmFuc2Zlci1gbmNvZGluZzpjaHVPUFRJT05TICogSFRUUNTU////////////////////////////////////////Z2dnZ2dnZ1RU/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////y8xLjEKY29udGVudC1sZW5ndGg6NAoKDUlPTlMgLyBIVFRQLzEuMQpUcmFuc2Zlci1FbmNvZGluZzpjaHVua2X/////T1NUIC8gSFRUUC8xLjEKQ29udGVudC1UeXBlOm47Kj04Cgr//////////////////////////////////////////////////////////2phY2tzb24uYmVhbi3//////////2phY2tzb24uYmVhbi1pbnRyb3NwZWN0aVRJT05TICogSGBUVFAvMS4xCmNvbnRlbnQtbGVuZ3RoOjQKCg1JT05TIC8gSFRUUC8xLjEKVHJhbnNmZXItYG5jb2Rpbmc6Y2h1T1BUSU9OUyAqIEhUVFDU1P///////////////////////////////////////2dnZ2dnZ2dUVP///////////////////////////////////////////////////////////////3Ryb3NwZWN0aVRJT05TICogSGBUVFAvMC4xCmNvbnRlbnQtbGVuZ3RoOjQKCg1JT05TIC8gSFRUUC8xLjEKVHJhbnNmZXItYG5jb2Rpbmc6Y2h1T1BUSU9OUyAqIEhUVFDU1P///////////////////////////////////////2dnZ2dnZ2dUVP////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////8vMS4xCmNvbnRlbnQtbGVuZ3RoOjQKCg1JT05TIC8gSFRUUC8xLjEKVHJhbnNmZXItRW5jb2Rpbmc6Y2h1bmtl/////09TVP///////////////////////////////////////2dnZ2dnZ2dUVP////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9nZ2dnZ2dnVFT/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////LzEuMQpjb250ZW50LWxlbmd0aDo0CgoNSU9OUyAvIEhUVFAvMS4xClRyYW5zZmVyLUVuY29kaW5nOmNodW5rZf////9PU1QgLyBIVFRQLzEuMQpDb250ZW50LVR5cGU6bjsqPTgKCv//////////////////////////////////////////////////////////amFja3Nvbi5iZWFuLf//////////amFja3Nvbi5iZWFuLWludHJvc3BlY3RpVElPTlMgKiBIYFRUUC8xLjEKY29udGVudC1sZW5ndGg6NAoKDUlPTlMgLyBIVFRQLzEuMQpUcmFuc2Zlci1gbmNvZGluZzpjaHVPUFRJT05TICogSFRUUNTU////////////////////////////////////////Z2dnZ2dnZ1RU////////////////////////////////////////////////////////////////dHJvc3BlY3RpVElPTlMgKiBIYFRUUC8wLjEKY29udGVudC1sZW5ndGg6NAoKDUlPTlMgLyBIVFRQLzEuMQpUcmFuc2Zlci1gbmNvZGluZzpjaHVPUFRJT05TICogSFRUUNTU////////////////////////////////////////Z2dnZ2dnZ1RU/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////y8xLjEKY29udGVudC1sZW5ndGg609PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09P//////2phY2tzb24uYmVhbi3//////////2phY2tzb24uYmVhbi1pbnRyb3JhbnNmZXItRW5jb2Rpbmc6Y2h1bmtl/////09TVCAvIEhUVFAvbjFDCi5vMXRlbnQtVHlwZTpuOyo9OAoK//////////////////////////////////////////////////////////9qYWNrc29uLmJlYW4t//////////9qYWNrc29uLmJlYW4taW50cm9zcGVjdGlvbi1tb2R1bG9uLW1vZCAvIEhUVFAvMS4xCkNvbnRlbnQtVHl1bGUu//9kCgo="),
        ]
    }
}
