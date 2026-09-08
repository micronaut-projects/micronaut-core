# tag::class[]
from micronaut.context.annotation import Bean
from jakarta.inject import Singleton


@Singleton
@Bean(preDestroy = "flush") # <1>
class Cache:
    flushed : bool = False

    def flush(self): # <2>
        self.flushed = True
# end::class[]
