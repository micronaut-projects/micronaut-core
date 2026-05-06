from abc import ABC, abstractmethod
from typing import Annotated

# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires, Value
# end::imports[]


class Engine(ABC):
    @abstractmethod
    def get_cylinders(self) -> int:
        pass

    @abstractmethod
    def start(self) -> str:
        pass


@Requires(property="spec.name", value="VehicleValueSpec")
# tag::class[]
@Singleton
class EngineImpl(Engine):
    cylinders: Annotated[int, Value("${my.engine.cylinders:6}")]  # <1>
    description: Annotated[str | None, Value("${my.engine.description}")] = None

    def get_cylinders(self) -> int:
        return self.cylinders

    def start(self) -> str:  # <2>
        return f"Starting V{self.get_cylinders()} Engine"

    def get_description(self) -> str | None:
        return self.description
# end::class[]
