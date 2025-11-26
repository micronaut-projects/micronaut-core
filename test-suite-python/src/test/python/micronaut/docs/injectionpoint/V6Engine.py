from .Engine import Engine

# tag::class[]
class V6Engine(Engine):
    cylinders: int = 6

    def start(self) -> str:
        return "Starting V6"

    def get_cylinders(self) -> int:
        return self.cylinders
# end::class[]
