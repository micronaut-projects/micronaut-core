package io.micronaut.docs.netty;

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.netty.channel.Channel
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookServerHandler

import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Requires(beans = Logbook.class)
@Singleton
class LogbookNettyServerCustomizer
        implements BeanCreatedEventListener<NettyServerCustomizer.Registry> { // <1>
    private final Logbook logbook;

    LogbookNettyServerCustomizer(Logbook logbook) {
        this.logbook = logbook
    }

    @Override
    NettyServerCustomizer.Registry onCreated(
            BeanCreatedEvent<NettyServerCustomizer.Registry> event) {

        NettyServerCustomizer.Registry registry = event.getBean()
        registry.register(new Customizer(null)) // <2>
        return registry
    }

    private class Customizer implements NettyServerCustomizer { // <3>
        private final Channel channel

        Customizer(Channel channel) {
            this.channel = channel
        }

        @Override
        NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new Customizer(channel) // <4>
        }

        @Override
        void onStreamPipelineBuilt() {
            channel.pipeline().addBefore( // <5>
                    ChannelPipelineCustomizer.HANDLER_HTTP_STREAM,
                    "logbook",
                    new LogbookServerHandler(logbook)
            )
        }
    }
}
// end::class[]
