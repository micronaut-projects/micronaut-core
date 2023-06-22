package io.micronaut.docs.netty

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener;

// tag::imports[]

import io.micronaut.http.client.netty.NettyClientCustomizer
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.netty.channel.Channel
import jakarta.inject.Singleton
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookClientHandler

// end::imports[]

// tag::class[]
@Requires(beans = Logbook.class)
@Singleton
class LogbookNettyClientCustomizer
        implements BeanCreatedEventListener<NettyClientCustomizer.Registry> { // <1>
    private final Logbook logbook;

    LogbookNettyClientCustomizer(Logbook logbook) {
        this.logbook = logbook
    }

    @Override
    NettyClientCustomizer.Registry onCreated(
            BeanCreatedEvent<NettyClientCustomizer.Registry> event) {

        NettyClientCustomizer.Registry registry = event.getBean()
        registry.register(new Customizer(null)) // <2>
        return registry
    }

    private class Customizer implements NettyClientCustomizer { // <3>
        private final Channel channel

        Customizer(Channel channel) {
            this.channel = channel
        }

        @Override
        NettyClientCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new Customizer(channel) // <4>
        }

        @Override
        void onRequestPipelineBuilt() {
            channel.pipeline().addBefore( // <5>
                    ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE,
                    "logbook",
                    new LogbookClientHandler(logbook)
            )
        }
    }
}
// end::class[]
