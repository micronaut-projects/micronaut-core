package io.micronaut.docs.netty

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.http.server.netty.NettyServerCustomizer.ChannelRole
import io.netty.channel.Channel
import jakarta.inject.Singleton
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookServerHandler
// end::imports[]

// tag::class[]
@Requires(beans = [Logbook::class])
@Singleton
class LogbookNettyServerCustomizer(private val logbook: Logbook) :
    BeanCreatedEventListener<NettyServerCustomizer.Registry> { // <1>

    override fun onCreated(event: BeanCreatedEvent<NettyServerCustomizer.Registry>): NettyServerCustomizer.Registry {
        val registry = event.bean
        registry.register(Customizer(null)) // <2>
        return registry
    }

    private inner class Customizer constructor(private val channel: Channel?) :
        NettyServerCustomizer { // <3>

        override fun specializeForChannel(channel: Channel, role: ChannelRole) = Customizer(channel) // <4>

        override fun onStreamPipelineBuilt() {
            channel!!.pipeline().addBefore( // <5>
                ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND,
                "logbook",
                LogbookServerHandler(logbook)
            )
        }
    }
}
// end::class[]
