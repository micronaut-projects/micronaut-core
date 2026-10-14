from abc import ABC, abstractmethod


class Codec(ABC):
    """A codec, the bean type CodecRegistry watches."""

    @abstractmethod
    def media_type(self) -> str:
        ...
