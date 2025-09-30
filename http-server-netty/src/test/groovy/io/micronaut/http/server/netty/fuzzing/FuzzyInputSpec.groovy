package io.micronaut.http.server.netty.fuzzing

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration
import io.micronaut.http.netty.channel.EventLoopGroupRegistry
import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.http.tck.netty.TestLeakDetector
import io.micronaut.http.server.util.DefaultHttpHostResolver
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
        TestLeakDetector.startTracking("")

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
        TestLeakDetector.stopTrackingAndReportLeaks()

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
        FlagAppender.clear()
        TestLeakDetector.startTracking("")

        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'FuzzyInputSpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        when:
        def embeddedChannel = embeddedServer.buildEmbeddedChannel(false)

        def b = embeddedChannel.alloc().buffer(input.length)
        b.writeBytes(input)
        embeddedChannel.writeInbound(b)
        embeddedChannel.runPendingTasks()

        embeddedChannel.releaseOutbound()
        // don't release inbound, that doesn't happen normally either
        for (Object inboundMessage : embeddedChannel.inboundMessages()) {
            ReferenceCountUtil.touch(inboundMessage)
        }
        embeddedChannel.finish()

        then:
        embeddedChannel.checkException()

        TestLeakDetector.stopTrackingAndReportLeaks()
        FlagAppender.checkTriggered()

        where:
        input << [
                Base64.decoder.decode("RyAqIFAvMS4xCmNvbnRlbnQtbGVuZ3RoOjQKCg1JT05TIC8gUC8xLjEKClMgLyBQLzEuMQpjb250ZW50LWxlbmd0aDo0CgoNZ3BJUyAvIFQvMS43CgpQT1NUIC8gUC8xLjEKY29udGVudC1sZW5ndGg6NAoKDUlPTlMgLyBILzEuMQpjb250ZW50LWxlbmd0aDo0Cgo="),
                Base64.decoder.decode("VCAvLy4gSFRUUC8xLjEKCg=="),
                Base64.decoder.decode("T1BUSU9OUyAqIEhUVFAvMS4xCgogKiBIVFQxCgo="),
                Base64.decoder.decode("SEVBRCAvIEhUVFAvMS4xCkhvc3Q6IGFHRVQgLyBIVFRQpC8xLjEKSG9zdDogYXBpLmJvbpKSkpKSkpKSkpL//////////////////////////3d3dy07biE9ZQoKUE9TVCAvIABUVFAvMS4zCmNvbnRlbnQtdHlwZTp0R09HR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHRy9HR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR7nBR0dHR0dHOxkZGRkZGRkZGW49bkdHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHRztePWY7kpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpJUVFAvMS4zCmNvbnRlbnQtdHlwZTp0R0dHR0dHR0dHR0dHR0dHR29uOiBCYXNpYyBYWFgvLyBUVFAvMS4xCgo="),
                Base64.decoder.decode("UE9TVCAvIEhUVFAvMS40NApBY2NlcHQ6IGFwcNTU1NTU1NTU1NTU1NTU0NTU////////////////////////////////Z2dnZy4xCkhvc3Q6IDQKQWNjZXB0OiAqLyoKdC5ldToyNDQ0CkFjY2VwdDogYXBwbGljYXRQT1NUIC8g///////////U1NTU1NTU1NTU1NTU1NTU0NTU////////////////////////////////////////Z2dnZy4xCkhvc3Q6IDQKQWNjZXB0OiAqLyoKdC5ldToyNDQ0CkFjY2VwdDogYXBwbGljYXRQT1NUIEdFVCAvIC8g/////////////////////yT/YXBw1NTU1NTU1NTU1NTU1NTQ1NT///////////////////////////////9nZ2dnLjEKSG9zdDogNApBY2NlcHQ6ICovKgp0LmV1OjI0NDQKQUhvc3Q6IDQKQWNjZXB0OiAqL3B0OiBhcHDU1NTU1NTU1NTU1NTU1NDU1P///////////////////////////////2dnZ2cuMQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwcGxpY2F0UE9TVCAvIP//////////1NTU1NTU1NTU1NTU1NTU1NDU1P///////////////////////////////////////2dnZ2cuMQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwcGxpY2F0UE9TVCBHRVQgLyAvIP////////////////////8k/2FwcNTU1NTU1NTU1NTU1NTU0NTU////////////////////////////////Z2dnZy4xCkhvc3Q6IDQKQWNjZXB0OiAqLyoKdC5ldToyNDQ0CkFIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwcGxpY2F0UE9TVCAvIP////////////////////8k/////////////////////////9TU1E9QVP//////////////Z2dnZy4xCkhvc3Q6IDQKQWNjZXB0OiAqVCAvIP///////yoKdC5ldToyNDQ0CkFjY2VwdDogYXBwbGljYXRQT1NUIC8g/////////////////////yT/////////////////////////1NTUT1BU//////////////9nZ2dnLjEKSG9zdDogNApBY2NlcHQ6ICovKgp0LmV1OjI0NDQKQWNjZXB0OiBldToyNDQ0CkFjY2VwdDogYXAscGxpY2F0UE9TVCAvIP////////////////////8k/////////////////////////9TU1E9QVElPTsQz1NTU1C8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwcGxpY2F0UE9TVCAvIP//MQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwLHBsaWNhdFBPU1QgLyD/////////////////////JP/////////////////////////U1NRPUFRJT07EM9TU1NTU1NTU1GFwaS5iZS1wcm9qZWN0LmV1OjQ0MwpBY2NlcHQ6IGFwcGxpY2F0UE9TVCgvIP//Ki8qCnQuZXU6MjQ0NApBY2NlcHQ6IGH////////////////////////U1NRPUFT//////////////2dnZ2cuMQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwcGxpY2F0UE9TVCAvIP//MQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwcGxpY2F0UCD/////////////////////JP/////////////////////////U1NRPUFT//////////////2dnZ2cuMQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwcGxpY2F0UE9TVCAvIP//MQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwLHBsaWNhdFBPU1QgLyD/////////////////////JP/////////////////////////U1NRPUFRJT07EM9TU1NTU1NTU1GFwaS5iZS1wcm9qZWN0LmV1OjQ0MwpBY2NlcHQ6IGFwcGxpY2F0UE9TVCgvIP//Ki8qCnQuZXU6MjQ0NApBY2NlcHQ6IGH////////////////////////U1NRPUFT//////////////2dnZ2cuMQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjQ0NApBY2NlcHQ6IGFwcGxpY2F0UE9TVCAvIP//MQpIb3N0OiA0CkFjY2VwdDogKi8qCnQuZXU6MjT///9nZ2dnLjEKSG9zdDogNApBY2NlcHQ6ICovKgp0LmV1OjI0NDQKQWNjZXB0OiBhcHBsaWNhdFBPU1QgLyD//////////9TU1NTU1NTU1NTU1NTU1NTQ1NT//////////w=="),
                Base64.decoder.decode("VCB4dCBQLzUuMQoKUDIg/CBIUFRQLzEuMgotdHlwZTo3ClRyYX5zZmVyLUVuRVRUbmc6ZGVmbGF0ZQoKL7lFUDIg/CBIUFRQLzEuMQotdHlwZTotdHlwZTo3ClRyYW5zZmVyeXBlOjf///////////////////////////////////////////////////////////////////////////////////////////8KVHJhbnNmZXItRW5jb2Rpbmc6ZGVmbGF0ZQpjb250ZW50LWxlbmd0aDo4CgoNSU9OUyAvILiqVFAvCgovuUVQMkdHR0d3AC07biE="),
                Base64.decoder.decode("UyAvIFAvMC4xMQpjb250ZW50LWxlbmd0aDo0ClRyYW5zZmVyLUVuY29kaW5nOmRlZmxhdGUKCi+5RVAyIPwgSC8xLjEK"),
                Base64.decoder.decode("cA1ACUhUVFAvOC4wCkhvc3Q6OgpPcmlnaW46Cgo="),
                Base64.decoder.decode("SEVDc3QNQP/9P/8JSFRUUC8wLjEKZXB0OgoKcG9zdA1A/T/9Oi8v/y9lY2hvLXB1Ymxpc2hlcglIVFRQLzAuMQp0OgpDb250ZW50LUxlbmd0aDo1Cgr/"),
                Base64.decoder.decode("SEVDRCBIIEhUVFAvMS4wCiY6MwoKcG9zdA1A//0//wlIVFRQLzAuMQplcHQ6Cgpwb3N0DUD9P/06Ly//L2VjaG8tcHVibGlzaGVyCUhUVFAvMC4xCnQ6CkNvbnRlbnQtTGVuZ3RoOjUKCv8="),
                Base64.decoder.decode("cG9zdA0vZWNoby1zdHJpbmcJSFRUUC8wLjEKdDpBCkNvbnRlbnQtTGVuZ3RoOjc2Cgpl"),
                Base64.decoder.decode("cG9zdA0vZWNoby1hcnJheQtIVFRQLzIuMApDb250ZW50LUxlbmd0aDo5Ck9yaWdpbjoKCg=="),
                "POST / HTTP/1.1\nTransfer-Encoding: snappy,chunked\n\n1\n2\n\n\n".bytes
        ]
    }

    def 'http2 cleartext embedded channel'() {
        given:
        FlagAppender.clear()
        TestLeakDetector.startTracking("")

        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'FuzzyInputSpec',
                "micronaut.server.http-version": "2.0"
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        when:
        def embeddedChannel = embeddedServer.buildEmbeddedChannel(false)

        def b = embeddedChannel.alloc().buffer(input.length)
        b.writeBytes(input)
        embeddedChannel.writeInbound(b)
        embeddedChannel.runPendingTasks()

        embeddedChannel.releaseOutbound()
        // don't release inbound, that doesn't happen normally either
        for (Object inboundMessage : embeddedChannel.inboundMessages()) {
            ReferenceCountUtil.touch(inboundMessage)
        }
        embeddedChannel.finish()

        then:
        embeddedChannel.checkException()

        TestLeakDetector.stopTrackingAndReportLeaks()
        FlagAppender.checkTriggered()

        where:
        input << [
                Base64.decoder.decode("RSArIEhUVFAvMS4xCkNvbnRlbnQtTGVuZ3RoOjIKdDr/ClVwZ3JhZGU6Cgo="),
                Base64.decoder.decode("UEFUQ0gLDQ0jLzovL9s6Ly8NAEhUVFAvMi4wCm8uVjpEIcAvJwpBY2NlcHQ6LHRleHQvaHRtbAoK" +
                        "RU1UDUD///8JSFRUUC8wLjEKQWNjZXB0Oglpby7///////9lcHQ6LGVwdDosZXB0Oix0ZXh0L2h0" +
                        "bWwsZS5ObgoKdA1A/////T/9Oi8v////CUhUVFAvMC4xCkFjY2VwdDoJaW9hbmdlOix0ZXh0L2h0" +
                        "bWwsVXNlci1BblVwZ3JhRW1iLjMKCnBvc3QNQC8v////CUhUVFAvMC4xCkFjY2VwdDoJaW8sdGV4" +
                        "dC9odG1sLCc6MwoKcG9zdA1A///9P/06Ly////8JSFRUUC8wLjEKQWNjZXB0OglnZTosdGV4dC9o" +
                        "dG1sLFVzZXBUQ0h0dHAuRTMKCnBvc3QNQP////0//TovL////wlIVFRQLzAuMQpBY2NlcHQ6CWlv" +
                        "ZXgtUmFuZ2U6LHRleHQvaHRtbCxVL/06Ly////8JUC8yLjMKCkVNVA1A/////T/9Ov8JSFRUUC8w" +
                        "LjEKQWNjZXB0OjI6LHRleHQvaHRtbCxVZXB0LUVuYy4zCgpFTVQNQP////0//TovL////wlIVFRQ" +
                        "LzAuMQpBY2NlcHQ6CWlVVENIYzosdGV4dC9odG1sLFUuMwoKcG9zdA1A/////T/9Oi8vCUhUVFAv" +
                        "MC4xCkFjY2VwZWM6clMu8QpBY2NlcHQ6LHRlZi1SbmdlOix0ZXh0L2h0bWwsVXNlRUFECTb9Oi/9" +
                        "MwoKcG8NQP////0//Tr///8JSFRUUC8wLjEKQWNjZXB0Oglpby7/////LHRleHQvaHRtbCxVc2VF" +
                        "dHBPLy///zMKCnBvc3QNL////wlIVFRQLzAuMQpBY2NlcHQ6CWlvLix0ZXh0L2h0bWwsVWVFQUQh" +
                        "wCcnJzosMwoKcG9zDUD////9P/06CUhUVFAvMC4xCkFjY2VwdDoJaW8u/zosdGV4dC9odG1sLFVz" +
                        "ZUVBRCHALzotMApDb250ZW50LUxlbmd0aDo0MwoK//4uAAh0aXJlbmNlQ291bnRVbM3ac3NXaG5n" +
                        "WVlZWVkJ/wAAAABSZWZlbXQCCf9sYXQLSFRUUC8yLjAKVHJhbnNmZS1FbmNuZzpzbmFwcHksdHBU" +
                        "YXJnZfEKQWNjZXB0Oix0ZXghwElmLVIsdGV4dC9odG1sLFVzZUVBRCEzCgpwb3N0DUD///0//Tov" +
                        "L///CUhUVFAvMC4xCkFjY2VwdDplOix0ZXh0L2h0bWwsVXRlcHRIRCFvLm0uMwoKcG9zdA0v////" +
                        "CUhUVFAvMC4xCkFjY2VwdDphcmdldHBPYmplYzosdGV4dC9odG1sLFVzL/0JUC8yLjMKCnBvc3QN" +
                        "QP///////wlIVFRQLzAuMQpBY2NlcGVjOnJTLvEKQWNjZXB0Oix0ZXghOix0ZXh0L2h0bWwsVXNl" +
                        "dDozCgpwb3N0Df8v////CUhUVFAvMC4xCkFjY2VwdDoJaf///yx0ZXh0L2h0bWwsVXMn/2Vy/wlQ" +
                        "LzIuMwoKcG9zdA1A/////T/9Oi//CUhUVFAvMC4xCkFjY2VwdDoJaW8u//8sdGV4dC9odG1sLFVl" +
                        "MwoKcG9zdA1A//8JSFRUUC8wLjEKQWNjZXB0Ov//QUNMfKuvZTJDb/U6LHRleHQvaHRtbCxVOm95" +
                        "LCwsSWYtTWF0L3JjdGg6LTAKQ29udGVudC1MZW5ndGg6NDMKCv/+LgAIdGlyZW5jZUNvdW50VXRp" +
                        "bM3ac3NXaG5nWVlZWVkJ/wAAAABSZWZlbXQCCf9sYXQLSFRUUC8yLjAKVHJhbnNmZXItRW5jb2Rp" +
                        "bmc6c25hcHB5LCw6LTAKQ29udGVudC1MZW5ndGg6NDQKCv/+LgAIdGlyZUhUVDosdA01Cg=="),
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

        @Post("/echo-publisher")
        public Publisher<byte[]> echo(@Body Publisher<byte[]> foo) {
            return foo;
        }

        @Post("/echo-string")
        public String echo(@Body String foo) {
            return foo;
        }

        @Post("/echo-array")
        public byte[] echo(@Body byte[] foo) {
            return foo;
        }
    }

    @Singleton
    @Replaces(DefaultHttpHostResolver.class)
    @Requires(property = "spec.name", value = "FuzzyInputSpec")
    public static class StableHttpHostResolver extends DefaultHttpHostResolver {
        private static final boolean LOCAL = false;

        public StableHttpHostResolver(HttpServerConfiguration serverConfiguration, @Nullable BeanProvider<EmbeddedServer> embeddedServer) {
            super(serverConfiguration, embeddedServer);
        }

        @Override
        protected String getEmbeddedHost() {
            return LOCAL ? "http://localhost:8080" : "http://example.com:8080";
        }
    }
}
