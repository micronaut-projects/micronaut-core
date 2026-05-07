# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
from micronaut.http.client.netty import NettyClientCustomizer
from micronaut.http.netty.channel import ChannelPipelineCustomizer
from org.zalando.logbook import Logbook
from org.zalando.logbook.netty import LogbookClientHandler
# end::imports[]


# tag::class[]
@Requires(beans=Logbook)
@Singleton
class LogbookNettyClientCustomizer(
    BeanCreatedEventListener[NettyClientCustomizer.Registry]
):  # <1>
    def __init__(self, logbook: Logbook):
        self.logbook = logbook

    def onCreated(self, event: BeanCreatedEvent[NettyClientCustomizer.Registry]):
        registry = event.getBean()
        registry.register(self.Customizer(self.logbook, None))  # <2>
        return registry

    class Customizer(NettyClientCustomizer):  # <3>
        def __init__(self, logbook: Logbook, channel):
            self.logbook = logbook
            self.channel = channel

        def specializeForChannel(self, channel, role):
            return LogbookNettyClientCustomizer.Customizer(self.logbook, channel)  # <4>

        def onRequestPipelineBuilt(self) -> None:
            self.channel.pipeline().addBefore(  # <5>
                ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE,
                "logbook",
                LogbookClientHandler(self.logbook),
            )
# end::class[]
