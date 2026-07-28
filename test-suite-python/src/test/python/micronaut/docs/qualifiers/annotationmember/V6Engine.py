from jakarta.inject import Singleton
from .Engine import Engine
from .Cylinders import Cylinders

# tag::class[]
@Singleton
@Cylinders(value = 6, description = "6-cylinder V6 engine")
class V6Engine(Engine):
    cylinders: int = 6

    def start(self) -> str:
        return "Starting V6"

    def get_cylinders(self) -> int:
        return self.cylinders
# end::class[]
