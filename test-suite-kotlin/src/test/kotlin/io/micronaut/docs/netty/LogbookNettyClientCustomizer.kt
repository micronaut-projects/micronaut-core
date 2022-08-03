package io.micronaut.docs.netty

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.client.netty.NettyClientCustomizer
import io.micronaut.http.client.netty.NettyClientCustomizer.ChannelRole
import io.netty.channel.Channel
import jakarta.inject.Singleton
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookClientHandler
// end::imports[]

// tag::class[]
@Requires(beans = [Logbook::class])
@Singleton
class LogbookNettyClientCustomizer(private val logbook: Logbook) :
    BeanCreatedEventListener<NettyClientCustomizer.Registry> { // <1>

    override fun onCreated(event: BeanCreatedEvent<NettyClientCustomizer.Registry>): NettyClientCustomizer.Registry {
        val registry = event.bean
        registry.register(Customizer(null)) // <2>
        return registry
    }

    private inner class Customizer constructor(private val channel: Channel?) :
        NettyClientCustomizer { // <3>

        override fun specializeForChannel(channel: Channel, role: ChannelRole) = Customizer(channel) // <4>

        override fun onStreamPipelineBuilt() {
            channel!!.pipeline().addLast( // <5>
                "logbook",
                LogbookClientHandler(logbook)
            )
        }
    }
}
// end::class[]
