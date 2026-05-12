from micronaut.context import ApplicationContext
from micronaut.core.io import ResourceLoadStrategy, ResourceLoadStrategyType
from micronaut.runtime import Micronaut


class ConfigurationLoadStrategySnippet:
    def first_match(self, args: list[str]) -> None:
        # tag::firstMatch[]
        Micronaut.build(args) \
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.FIRST_MATCH)
                .warnOnDuplicates(True)) \
            .start()
        # end::firstMatch[]

    def merge_all(self) -> None:
        # tag::mergeAll[]
        ctx = ApplicationContext.builder() \
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.MERGE_ALL)) \
            .start()
        # end::mergeAll[]

        ctx.close()

    def merge_order(self) -> None:
        # tag::mergeOrder[]
        ctx = ApplicationContext.builder() \
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.MERGE_ALL)
                .mergeOrder("lib-.*\\.jar", "app-.*\\.jar")) \
            .start()
        # end::mergeOrder[]

        ctx.close()

    def restore_first_match(self) -> None:
        # tag::restoreFirstMatch[]
        ctx = ApplicationContext.builder() \
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.FIRST_MATCH)) \
            .start()
        # end::restoreFirstMatch[]

        ctx.close()
