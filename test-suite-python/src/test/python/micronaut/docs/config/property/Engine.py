from typing import Annotated

# tag::imports[]
from jakarta.inject import Inject, Singleton
from micronaut.context.annotation import Property, Requires
# end::imports[]


@Requires(property="spec.name", value="VehicleConfigPropertySpec")
# tag::class[]
@Singleton
class Engine:
    cylinders: Annotated[int, Property(name="my.engine.cylinders")]  # <1> <2>
    manufacturer: str

    def get_cylinders(self) -> int:
        return self.cylinders

    def get_manufacturer(self) -> str:
        return self.manufacturer

    @Inject
    def set_manufacturer(
        self,
        manufacturer: Annotated[str, Property(name="my.engine.manufacturer")],
    ) -> None:  # <3>
        self.manufacturer = manufacturer
# end::class[]
