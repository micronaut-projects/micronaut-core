from .Engine import Engine

# tag::class[]
class V8Engine(Engine):
    cylinders: int = 8

    def start(self) -> str:
        return "Starting V8"

    def get_cylinders(self) -> int:
        return self.cylinders
# end::class[]
