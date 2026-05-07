from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from typing import Annotated

from micronaut.docs.qualifiers.annotationmember.Engine import Engine

# tag::imports[]
from jakarta.inject import Inject
from micronaut.context.annotation import Any
# end::imports[]


@Property(name="spec.name", value="VehicleAnySpec")
@MicronautTest
class VehicleSpec:
    # tag::any[]
    engine: Annotated[Engine, Inject, Any] = None
    # end::any[]

    @Test
    def test_engine(self) -> None:
        assert self.engine is not None
