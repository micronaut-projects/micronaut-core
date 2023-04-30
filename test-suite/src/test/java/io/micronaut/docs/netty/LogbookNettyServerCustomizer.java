package io.micronaut.docs.netty;

// tag::imports[]

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.netty.NettyServerCustomizer;
import io.netty.channel.Channel;
import jakarta.inject.Singleton;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookServerHandler;
// end::imports[]

// tag::class[]
@Requires(beans = Logbook.class)
@Singleton
public class LogbookNettyServerCustomizer
    implements BeanCreatedEventListener<NettyServerCustomizer.Registry> { // <1>
    private final Logbook logbook;

    public LogbookNettyServerCustomizer(Logbook logbook) {
        this.logbook = logbook;
    }

    @Override
    public NettyServerCustomizer.Registry onCreated(
        BeanCreatedEvent<NettyServerCustomizer.Registry> event) {

        NettyServerCustomizer.Registry registry = event.getBean();
        registry.register(new Customizer(null)); // <2>
        return registry;
    }

    private class Customizer implements NettyServerCustomizer { // <3>
        private final Channel channel;

        Customizer(Channel channel) {
            this.channel = channel;
        }

        @Override
        public NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new Customizer(channel); // <4>
        }

        @Override
        public void onStreamPipelineBuilt() {
            channel.pipeline().addBefore( // <5>
                ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND,
                "logbook",
                new LogbookServerHandler(logbook)
            );
        }
    }
}
// end::class[]
