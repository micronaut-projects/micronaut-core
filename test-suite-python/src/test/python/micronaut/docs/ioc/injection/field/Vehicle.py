from jakarta.inject import Singleton, Inject
from typing import Annotated

@Singleton
class Engine:
    def start(self) -> str:
        return "Vrooom!"

@Singleton
class Vehicle:
    engine : Annotated[Engine, Inject] # <1>

    def start(self) -> str:
        return self.engine.start()


