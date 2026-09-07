# tag::class[]
from micronaut.core.annotation import Creator, Introspected


@Introspected
class Vehicle:

    def __init__(self, make: str, model: str, axles: int):
        self.make = make
        self.model = model
        self.axles = axles

    @classmethod
    @Creator  # <1>
    def of(cls, make: str, model: str, axles: int) -> "Vehicle":
        return cls(make, model, axles)
# end::class[]
