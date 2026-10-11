from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context import WatchableBeanContext

from .Serializer import Serializer
# end::imports[]


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
# tag::class[]
@Singleton
class SerializerLookup:

    def __init__(self, serializers: list[Serializer], context: WatchableBeanContext):
        self.serializers = serializers

        def on_change(change):
            definitions = context.getBeanDefinitions(Serializer)
            if any(change.isStaleDefinition(definition) for definition in definitions):
                context.recreate(SerializerLookup, None)  # <1>

        context.classChanges().watch(on_change)

    def serializer_for(self, value: object) -> Serializer:
        return next(serializer for serializer in self.serializers if serializer.supports(value))
# end::class[]
