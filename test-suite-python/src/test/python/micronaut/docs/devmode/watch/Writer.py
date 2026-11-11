from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .SerializerLookup import SerializerLookup


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
@Singleton
class Writer:
    """A bean that received SerializerLookup: recreating the lookup recreates it too."""

    def __init__(self, lookup: SerializerLookup):
        self.lookup = lookup

    def write(self, value: object) -> str:
        return self.lookup.serializer_for(value).serialize(value)
