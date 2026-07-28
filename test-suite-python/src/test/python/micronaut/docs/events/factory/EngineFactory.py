from jakarta.annotation import PostConstruct
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory

from .V8Engine import V8Engine


# tag::class[]
@Factory
class EngineFactory:
    def __init__(self) -> None:
        self.engine: V8Engine | None = None
        self.rod_length = 5.7

    @PostConstruct
    def initialize(self) -> None:
        self.engine = V8Engine(self.rod_length)  # <2>

    @Singleton
    def v8_engine(self) -> V8Engine:
        return self.engine  # <3>
# end::class[]
