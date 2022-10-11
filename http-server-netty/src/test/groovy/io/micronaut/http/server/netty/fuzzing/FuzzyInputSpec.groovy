package io.micronaut.http.server.netty.fuzzing

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.resolver.NoopAddressResolverGroup
import io.netty.util.ReferenceCountUtil
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import spock.lang.Specification

/**
 * HTTP inputs generated from fuzzing.
 */
class FuzzyInputSpec extends Specification {

    def 'http1 cleartext buffer leaks'() {
        given:
        BufferLeakDetection.startTracking()

        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'FuzzyInputSpec',
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

    def 'http1 cleartext embedded channel'() {
        given:
        BufferLeakDetection.startTracking()

        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'FuzzyInputSpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        when:
        def embeddedChannel = embeddedServer.buildEmbeddedChannel(false)

        embeddedChannel.writeOneInbound(Unpooled.wrappedBuffer(input));
        embeddedChannel.runPendingTasks();

        embeddedChannel.releaseOutbound()
        // don't release inbound, that doesn't happen normally either
        for (Object inboundMessage : embeddedChannel.inboundMessages()) {
            ReferenceCountUtil.touch(inboundMessage)
        }
        embeddedChannel.finish()

        then:
        embeddedChannel.checkException()

        BufferLeakDetection.stopTrackingAndReportLeaks()

        cleanup:
        // normally this is set on the event loop and so doesn't persist across tests, but with EmbeddedChannel we don't make a new thread
        ServerRequestContext.set(null)

        where:
        input << [
                Base64.decoder.decode("RyAqIFAvMS4xCmNvbnRlbnQtbGVuZ3RoOjQKCg1JT05TIC8gUC8xLjEKClMgLyBQLzEuMQpjb250ZW50LWxlbmd0aDo0CgoNZ3BJUyAvIFQvMS43CgpQT1NUIC8gUC8xLjEKY29udGVudC1sZW5ndGg6NAoKDUlPTlMgLyBILzEuMQpjb250ZW50LWxlbmd0aDo0Cgo="),
        ]
    }

    @Singleton
    @Controller
    @Requires(property = 'spec.name', value = 'FuzzyInputSpec')
    public static class SimpleController {
        @Get
        public String index() {
            return "index"
        }

        @Post
        public Publisher<String> index(Publisher<String> foo) {
            return foo
        }
    }
}
