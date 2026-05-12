from abc import ABC, abstractmethod


class Engine(ABC):
    @abstractmethod
    def start(self) -> str:
        ...

    @abstractmethod
    def get_sensors(self):
        ...
