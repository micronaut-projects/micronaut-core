
# tag::imports[]
from jakarta.inject import Singleton
from jakarta.annotation import PostConstruct # <1>
# end::imports[]

from micronaut.docs.inject.typed import Engine

# tag::class[]
@Singleton
class V8Engine(Engine):
    cylinders: int = 8
    initialized: bool = False # <2>

    def start(self) -> str:
        if self.initialized is False:
            raise Exception("Engine not initialized!")
        return "Starting V8"

    def get_cylinders(self) -> int:
        return self.cylinders

    @PostConstruct
    def initialize(self): # <3>
        self.initialized = True
# end::class[]
