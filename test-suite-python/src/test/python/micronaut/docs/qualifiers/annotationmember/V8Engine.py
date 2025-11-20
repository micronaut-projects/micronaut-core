from jakarta.inject import Singleton
from .Engine import Engine
from .Cylinders import Cylinders

# tag::class[]
@Singleton
@Cylinders(value = 8, description = "8-cylinder V8 engine")
class V8Engine(Engine):
    cylinders: int = 8

    def start(self) -> str:
        return "Starting V8"

    def get_cylinders(self) -> int:
        return self.cylinders
# end::class[]
