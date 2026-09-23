# tag::imports[]
from java.io import IOException
from jakarta.inject import Singleton
from docs.javabases import ChannelInitializer
# end::imports[]


# tag::class[]
@Singleton
class QueueInitializer(ChannelInitializer):
    def initialize(self, channel: str, name: str) -> None:  # <1>
        if name.startswith("reserved"):
            raise IOException(f"{name} is reserved")  # <2>
        super().initialize(channel, "queue:" + name)  # <3>
# end::class[]
