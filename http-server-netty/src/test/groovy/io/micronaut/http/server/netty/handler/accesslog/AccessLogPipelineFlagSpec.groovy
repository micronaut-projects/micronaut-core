package io.micronaut.http.server.netty.handler.accesslog

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import io.netty.util.AttributeKey
import jakarta.inject.Singleton
import spock.lang.Specification

/**
 * The access log handler needs the current request in a channel attribute (Micronaut Session
 * reads it from there). Whether a pipeline carries that handler is recorded when the pipeline is
 * built, so this checks that the attribute is set exactly on the pipelines that have the handler.
 */
class AccessLogPipelineFlagSpec extends Specification {
    private static final AttributeKey<NettyHttpRequest> KEY = AttributeKey.valueOf(NettyHttpRequest.class.getSimpleName())

    def "request attribute is set only on pipelines with the access log handler"(Map<String, Object> properties, String expectedVersion, boolean expectAttribute) {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                                       : 'AccessLogPipelineFlagSpec',
                'micronaut.server.netty.access-logger.logger-name': 'http-access-log',
        ] + properties)
        def server = ctx.getBean(EmbeddedServer).start()
        def client = ctx.createBean(HttpClient, server.URI)

        when:
        def response = client.toBlocking().retrieve('/access-log-flag')

        then:
        response == "$expectedVersion $expectAttribute"

        cleanup:
        client.close()
        ctx.close()

        where:
        properties                                                                                                                                                                    | expectedVersion | expectAttribute
        ['micronaut.server.netty.access-logger.enabled': true]                                                                                                                        | 'HTTP_1_1'      | true
        ['micronaut.server.netty.access-logger.enabled': false]                                                                                                                       | 'HTTP_1_1'      | false
        // the default HTTP/2 handler logs through the Http2AccessLogManager on the connection pipeline, which has no per-stream channel for the attribute
        ['micronaut.server.netty.access-logger.enabled': true, 'micronaut.server.http-version': '2.0', 'micronaut.server.ssl.enabled': false, 'micronaut.http.client.plaintext-mode': 'h2c_prior_knowledge']                                                             | 'HTTP_2_0'      | false
        // the legacy HTTP/2 handlers build a pipeline with the access log handler per stream
        ['micronaut.server.netty.access-logger.enabled': true, 'micronaut.server.http-version': '2.0', 'micronaut.server.ssl.enabled': false, 'micronaut.http.client.plaintext-mode': 'h2c_prior_knowledge', 'micronaut.server.netty.legacy-multiplex-handlers': true] | 'HTTP_2_0'      | true
        // a customizer that removes the handler from the pipeline must be respected
        ['micronaut.server.netty.access-logger.enabled': true, 'access-log-flag.remove-handler': true]                                                                                | 'HTTP_1_1'      | false
    }

    @Singleton
    @Requires(property = 'access-log-flag.remove-handler', value = 'true')
    static class RemovingCustomizer implements BeanCreatedEventListener<NettyServerCustomizer.Registry>, NettyServerCustomizer {
        @Override
        NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.bean.register(this)
            return event.bean
        }

        @Override
        NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new Removing(channel)
        }

        static class Removing implements NettyServerCustomizer {
            final Channel channel

            Removing(Channel channel) {
                this.channel = channel
            }

            @Override
            NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                return new Removing(channel)
            }

            @Override
            void onStreamPipelineBuilt() {
                if (channel.pipeline().get(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER) != null) {
                    channel.pipeline().remove(ChannelPipelineCustomizer.HANDLER_ACCESS_LOGGER)
                }
            }
        }
    }

    @Controller('/access-log-flag')
    @Requires(property = 'spec.name', value = 'AccessLogPipelineFlagSpec')
    static class TestController {
        @Get
        String get(HttpRequest<?> request) {
            def nettyRequest = (NettyHttpRequest<?>) request
            def attribute = nettyRequest.channelHandlerContext.channel().attr(KEY).get()
            // the attribute must hold this very request while it is being routed
            return "${request.httpVersion} ${attribute.is(request)}"
        }
    }
}
