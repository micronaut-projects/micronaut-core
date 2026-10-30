from abc import ABC, abstractmethod


class Serializer(ABC):
    """A serializer, the bean type SerializerRegistry and SerializerLookup derive state from."""

    @abstractmethod
    def supports(self, value: object) -> bool:
        ...

    @abstractmethod
    def serialize(self, value: object) -> str:
        ...
