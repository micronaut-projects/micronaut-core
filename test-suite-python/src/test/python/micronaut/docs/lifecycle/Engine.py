from abc import ABC, abstractmethod


# tag::class[]
class Engine(ABC):  # <1>
    @abstractmethod
    def get_cylinders(self) -> int:
        pass

    @abstractmethod
    def start(self) -> str:
        pass
# end::class[]
