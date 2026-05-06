# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
from micronaut.http.netty.channel import ChannelPipelineCustomizer
from micronaut.http.server.netty import NettyServerCustomizer
from org.zalando.logbook import Logbook
from org.zalando.logbook.netty import LogbookServerHandler
# end::imports[]


# tag::class[]
@Requires(beans=Logbook)
@Singleton
class LogbookNettyServerCustomizer:  # <1>
    # TODO: Re-enable this direct port when Python BeanCreatedEventListener
    # generic adaptation generates a valid bean definition.
    # class LogbookNettyServerCustomizer(
    #     BeanCreatedEventListener[NettyServerCustomizer.Registry]
    # ):

    def __init__(self, logbook: Logbook):
        self.logbook = logbook

    def onCreated(self, event: BeanCreatedEvent[NettyServerCustomizer.Registry]):
        registry = event.getBean()
        registry.register(self.Customizer(self.logbook, None))  # <2>
        return registry

    class Customizer(NettyServerCustomizer):  # <3>
        def __init__(self, logbook: Logbook, channel):
            self.logbook = logbook
            self.channel = channel

        def specializeForChannel(self, channel, role):
            return LogbookNettyServerCustomizer.Customizer(self.logbook, channel)  # <4>

        def onStreamPipelineBuilt(self) -> None:
            self.channel.pipeline().addBefore(  # <5>
                ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND,
                "logbook",
                LogbookServerHandler(self.logbook),
            )
# end::class[]
