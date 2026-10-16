from micronaut.context.annotation import EachBean

from .ChannelConfiguration import ChannelConfiguration
from .Labelled import Labelled


# tag::channel[]
@EachBean(ChannelConfiguration.__qualname__)  # <1>
@Labelled
class Channel:
    def __init__(self, configuration: ChannelConfiguration):
        self.configuration = configuration

    def send(self, message: str) -> str:
        return f"{message} from {self.configuration.sender}"
# end::channel[]
