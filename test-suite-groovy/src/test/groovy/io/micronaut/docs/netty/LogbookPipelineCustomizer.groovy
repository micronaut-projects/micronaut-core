package io.micronaut.docs.netty

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.*
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.netty.channel.ChannelPipeline
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.*
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Requires(beans = Logbook.class)
@Singleton
class LogbookPipelineCustomizer implements BeanCreatedEventListener<ChannelPipelineCustomizer> { // <1>
    private final Logbook logbook

    LogbookPipelineCustomizer(Logbook logbook) {
        this.logbook = logbook
    }

    @Override
    ChannelPipelineCustomizer onCreated(BeanCreatedEvent<ChannelPipelineCustomizer> event) {
        ChannelPipelineCustomizer customizer = event.getBean()

        if (customizer.isServerChannel()) { // <2>
            customizer.doOnConnect( { ChannelPipeline pipeline ->
                pipeline.addAfter(
                        ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC,
                        "logbook",
                        new LogbookServerHandler(logbook)
                )
                return pipeline
            })
        } else { // <3>
            customizer.doOnConnect({ ChannelPipeline pipeline ->
                pipeline.addAfter(
                        ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC,
                        "logbook",
                        new LogbookClientHandler(logbook)
                )
                return pipeline
            })
        }
        return customizer
    }
}
// end::class[]
