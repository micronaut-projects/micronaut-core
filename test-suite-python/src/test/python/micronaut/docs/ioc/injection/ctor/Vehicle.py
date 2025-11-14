from jakarta.inject import Singleton, Inject
from typing import Annotated
from dataclasses import dataclass
from micronaut.core.annotation import Creator

@dataclass
@Singleton
class Engine:
    cylinders: int

    @classmethod
    @Creator # <2>
    def get_default(cls) -> "Engine":
        return cls(8)

    @classmethod
    def create(cls, cylinders: int) -> "Engine":
        return cls(cylinders)

    def start(self) -> str:
        return f"Vrooom! {self.cylinders}"

@Singleton
class Vehicle:
    def __init__(self, engine: Engine):  # <1>
        self.engine = engine

    @classmethod
    def create(cls) -> "Vehicle":
        return cls(Engine.create(6))

    def start(self) -> str:
        return self.engine.start()


