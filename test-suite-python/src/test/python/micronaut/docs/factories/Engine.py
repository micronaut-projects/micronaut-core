# tag::class[]
from abc import ABC, abstractmethod

class Engine(ABC): # <1>
    @abstractmethod
    def start(self) -> str:
        pass
# end::class[]
