# tag::class[]
from abc import ABC, abstractmethod


class Engine(ABC):  # <1>
    @abstractmethod
    def get_cylinders(self) -> int:
        ...

    @abstractmethod
    def start(self) -> str:
        ...
# end::class[]
