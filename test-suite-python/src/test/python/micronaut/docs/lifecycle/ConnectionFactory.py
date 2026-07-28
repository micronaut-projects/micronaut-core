# tag::class[]
from micronaut.context.annotation import Bean, Factory
from jakarta.inject import Singleton
from .Connection import Connection

@Factory
class ConnectionFactory:

    @Bean(preDestroy = "stop") # <1>
    @Singleton
    def connection(self) -> Connection:
        return Connection()
# end::class[]
