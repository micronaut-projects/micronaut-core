# tag::class[]
from .Stub import Stub
from abc import ABC, abstractmethod
from datetime import datetime

@Stub()
class StubExample(ABC):
    @abstractmethod
    @Stub("10")
    def get_number(self) -> int:
        ...

    @abstractmethod
    def get_date(self) -> datetime:
        ...
# end::class[]
