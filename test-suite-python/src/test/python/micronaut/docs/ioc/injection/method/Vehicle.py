from jakarta.inject import Singleton, Inject

@Singleton
class Engine:
    def start(self) -> str:
        return "Vrooom!"

@Singleton
class Vehicle:
    def __init__(self):
        self.engine = None

    @Inject
    def initialize(self, engine: Engine): # <1>
        self.engine = engine

    def start(self) -> str:
        return self.engine.start()

