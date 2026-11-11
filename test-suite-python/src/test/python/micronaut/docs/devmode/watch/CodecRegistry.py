from jakarta.inject import Singleton
from micronaut.context import WatchableBeanContext
from micronaut.context.annotation import Requires

from .Codec import Codec


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
@Singleton
class CodecRegistry:

    def __init__(self, context: WatchableBeanContext):
        self.codecs = set()
        # tag::handlers[]
        (context.definitions(Codec)
            .onAdded(lambda definition: self.codecs.add(definition))
            .onRemoved(lambda definition: self.codecs.discard(definition))
            .watch())
        # end::handlers[]

    def size(self) -> int:
        return len(self.codecs)
