# tag::class[]
from jakarta.inject import Singleton
from jakarta.annotation import PreDestroy # <1>


@Singleton
class PreDestroyBean:
    stopped : bool = False

    @PreDestroy
    def close(self): # <2>
        self.stopped = True
# end::class[]
