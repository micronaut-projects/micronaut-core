from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context import WatchableBeanContext
from micronaut.context.watch import BeanDefinitionChange
from micronaut.web.router import RouteBuilder
# end::imports[]


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
# tag::class[]
@Singleton
class RouteTable:

    def __init__(self, context: WatchableBeanContext):
        self.builders = []
        context.definitions(RouteBuilder).watch(self.on_change)  # <1>

    def on_change(self, change: BeanDefinitionChange) -> None:
        self.rebuild(change.current())  # <2>

    def rebuild(self, current) -> None:
        self.builders = list(current)

    def size(self) -> int:
        return len(self.builders)
# end::class[]
