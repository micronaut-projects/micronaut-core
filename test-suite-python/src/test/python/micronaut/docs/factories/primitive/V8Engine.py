# tag::imports[]
from jakarta.inject import Named, Singleton
from typing import Annotated
# end::imports[]

# tag::class[]
@Singleton
class V8Engine:
    def __init__(self, cylinders: Annotated[int, Named("V8")]):
        self.cylinders = cylinders

    def get_cylinders(self) -> int:
        return self.cylinders
# end::class[]
