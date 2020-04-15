package io.micronaut.docs.netty

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.ChannelPipeline
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.*
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Requires(beans = [Logbook::class])
@Singleton
class LogbookPipelineCustomizer(private val logbook: Logbook) : BeanCreatedEventListener<ChannelPipelineCustomizer> { // <1>

    override fun onCreated(event: BeanCreatedEvent<ChannelPipelineCustomizer>): ChannelPipelineCustomizer {
        val customizer = event.bean
        if (customizer is EmbeddedServer) { // <2>
            customizer.doOnConnect { pipeline: ChannelPipeline ->
                pipeline.addAfter(
                        ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC,
                        "logbook",
                        LogbookServerHandler(logbook)
                )
                pipeline
            }
        } else if (customizer is HttpClient) { // <3>
            customizer.doOnConnect { pipeline: ChannelPipeline ->
                pipeline.addAfter(
                        ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC,
                        "logbook",
                        LogbookClientHandler(logbook)
                )
                pipeline
            }
        }
        return customizer
    }
}
// end::class[]