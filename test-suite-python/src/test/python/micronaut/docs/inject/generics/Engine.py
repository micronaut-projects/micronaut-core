# tag::class[]
from typing import Generic, TypeVar, List
from abc import ABC, abstractmethod
from .CylinderProvider import CylinderProvider

T = TypeVar('T', bound=CylinderProvider)

class Engine(Generic[T], ABC): # <1>
    def get_cylinders(self) -> int:
        return self.get_cylinder_provider().get_cylinders()

    def start(self) -> str:
        return f"Starting {type(self.get_cylinder_provider()).__name__}"

    @abstractmethod
    def get_cylinder_provider(self) -> T:
        pass
# end::class[]
