from typing import Annotated

from micronaut.context.annotation import EachProperty
from micronaut.context.annotation import Parameter


@EachProperty("labelled-channels")
class ChannelConfiguration:
    sender: str = None

    def __init__(self, name: Annotated[str, Parameter]):
        self.name = name

    def set_sender(self, sender: str) -> None:
        self.sender = sender
