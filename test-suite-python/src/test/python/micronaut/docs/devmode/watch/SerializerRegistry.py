from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context import WatchableBeanContext

from .Serializer import Serializer
# end::imports[]


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
# tag::class[]
@Singleton
class SerializerRegistry:

    def __init__(self, context: WatchableBeanContext):
        self.serializers = []
        # no read of its own: the first batch is the read, and every change after it follows
        context.definitions(Serializer).watch(lambda change: self.update(change.current()))

    def update(self, definitions) -> None:
        self.serializers = sorted(definitions, key=lambda definition: definition.getBeanType().getName())

    def names(self) -> list[str]:
        return [definition.getBeanType().getName() for definition in self.serializers]
# end::class[]
