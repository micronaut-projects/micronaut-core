from abc import ABC, abstractmethod


class Engine(ABC):
    @abstractmethod
    def get_cylinders(self) -> int:
        ...

    @abstractmethod
    def start(self) -> str:
        ...
