# tag::imports[]
from typing import Generic, TypeVar
from java.lang import Number
from java.util.function import Supplier
from micronaut.core.annotation import TypeHierarchyImport
# end::imports[]

# tag::class[]
T = TypeVar("T", bound=Number)

class Base(Generic[T]):
    def value(self) -> T:
        return None

@TypeHierarchyImport  # <1>
class Payload(Base[T], Supplier[T], Generic[T]):
    def get(self) -> T:
        return None
# end::class[]
