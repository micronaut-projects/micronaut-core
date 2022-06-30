package io.micronaut.docs.netty;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer;
import io.micronaut.http.server.netty.ServerNettyCustomizer;
import io.netty.channel.Channel;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.netty.LogbookServerHandler;

import jakarta.inject.Singleton;
// end::imports[]

// tag::class[]
@Requires(beans = Logbook.class)
@Singleton
public class LogbookServerNettyCustomizer
    implements BeanCreatedEventListener<ServerNettyCustomizer.Registry> { // <1>
    private final Logbook logbook;

    public LogbookServerNettyCustomizer(Logbook logbook) {
        this.logbook = logbook;
    }

    @Override
    public ServerNettyCustomizer.Registry onCreated(
        BeanCreatedEvent<ServerNettyCustomizer.Registry> event) {

        ServerNettyCustomizer.Registry registry = event.getBean();
        registry.register(new Customizer(null)); // <2>
        return registry;
    }

    private class Customizer implements ServerNettyCustomizer { // <3>
        private final Channel channel;

        Customizer(Channel channel) {
            this.channel = channel;
        }

        @Override
        public ServerNettyCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new Customizer(channel); // <4>
        }

        @Override
        public void onStreamPipelineBuilt() {
            channel.pipeline().addBefore( // <5>
                ChannelPipelineCustomizer.HANDLER_HTTP_STREAM,
                "logbook",
                new LogbookServerHandler(logbook)
            );
        }
    }
}
// end::class[]
