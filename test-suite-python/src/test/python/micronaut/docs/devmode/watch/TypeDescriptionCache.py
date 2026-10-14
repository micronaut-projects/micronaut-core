from jakarta.inject import Singleton
from micronaut.context import WatchableBeanContext
from micronaut.context.annotation import Requires


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
@Singleton
class TypeDescriptionCache:
    """A cache keyed by class, which forgets the classes a reload retires."""

    def __init__(self, context: WatchableBeanContext):
        self.cache = {}
        # tag::watch[]
        context.classChanges().watch(lambda change: self.evict(change.isStaleType))
        # end::watch[]

    def evict(self, is_stale) -> None:
        for type_ in [type_ for type_ in self.cache if is_stale(type_)]:
            del self.cache[type_]

    def describe(self, type_: object) -> str:
        return self.cache.setdefault(type_, type_.getName())

    def size(self) -> int:
        return len(self.cache)
