from micronaut.context.annotation import Requires
# tag::imports[]
from micronaut.context import WatchableBeanContext
from micronaut.context.annotation import EachBean, Factory
from micronaut.context.watch import ReloadingConfigurationWatcher

from .ConnectionPool import ConnectionPool
from .PoolConfiguration import PoolConfiguration
# end::imports[]

Outcome = ReloadingConfigurationWatcher.Outcome


@Requires(property="spec.name", value="BeanWatchSnippetsSpec")
# tag::class[]
@Factory
class ConnectionPoolFactory:

    @EachBean(PoolConfiguration.__qualname__)
    def connection_pool(self, configuration: PoolConfiguration, context: WatchableBeanContext) -> ConnectionPool:
        pool = ConnectionPool(configuration.url, configuration.username, configuration.password)
        prefix = "pools." + configuration.name

        def on_change(change):  # <1>
            if change.touches(prefix + ".url"):
                return Outcome.RECREATE  # <2>
            if change.touchesAny(prefix + ".username", prefix + ".password"):
                pool.set_credentials(configuration.username, configuration.password)
                pool.soft_evict_connections()
                return Outcome.APPLIED  # <3>
            return Outcome.IGNORED  # <4>

        context.configuration(prefix).watchReloading(on_change)
        return pool
# end::class[]
