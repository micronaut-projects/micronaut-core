package io.micronaut.docs.netty

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.ServerNettyCustomizer
import io.micronaut.http.server.netty.ServerNettyCustomizer.ChannelRole
import io.netty.channel.Channel
import jakarta.inject.Singleton
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookServerHandler
// end::imports[]

// tag::class[]
@Requires(beans = [Logbook::class])
@Singleton
class LogbookServerNettyCustomizer(private val logbook: Logbook) :
    BeanCreatedEventListener<ServerNettyCustomizer.Registry> { // <1>

    override fun onCreated(event: BeanCreatedEvent<ServerNettyCustomizer.Registry>): ServerNettyCustomizer.Registry {
        val registry = event.bean
        registry.register(Customizer(null)) // <2>
        return registry
    }

    private inner class Customizer constructor(private val channel: Channel?) : ServerNettyCustomizer { // <3>

        override fun specializeForChannel(channel: Channel, role: ChannelRole) = Customizer(channel) // <4>

        override fun onStreamPipelineBuilt() {
            channel!!.pipeline().addBefore( // <5>
                ChannelPipelineCustomizer.HANDLER_HTTP_STREAM,
                "logbook",
                LogbookServerHandler(logbook)
            )
        }
    }
}
// end::class[]
