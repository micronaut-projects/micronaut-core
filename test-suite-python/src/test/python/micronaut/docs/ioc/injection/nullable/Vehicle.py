from jakarta.inject import Singleton
from dataclasses import dataclass

@dataclass
class Engine:
    cylinders: int

    def start(self) -> str:
        return f"Vrooom! {self.cylinders}"

@Singleton
class Vehicle:
    def __init__(self, engine: Engine | None):  # <1>
        self.engine = engine if engine is not None else Engine(6)

    def start(self) -> str:
        return self.engine.start()


