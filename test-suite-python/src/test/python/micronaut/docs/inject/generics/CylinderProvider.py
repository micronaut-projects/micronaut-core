from abc import ABC, abstractmethod

class CylinderProvider(ABC):
    @abstractmethod
    def get_cylinders(self) -> int:
        pass
