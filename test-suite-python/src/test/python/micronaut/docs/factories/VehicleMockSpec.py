# tag::imports[]
from org.junit.jupiter.api import Test, Disabled
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Bean, Requires, Replaces
from jakarta.inject import Inject
from .Vehicle import Vehicle
from .Engine import Engine
from typing import Annotated

# end::imports[]

# tag::class[]
class MockEngine(Engine):
    def start(self) -> str:
        return "Mock Started"

# @MicronautTest
@Disabled("Beans from fields not supported in Python at the moment")
class VehicleMockSpec:
    mock_engine : Annotated[Engine, Bean, Replaces("micronaut.docs.factories.Engine"), Requires(beans = "micronaut.docs.factories.VehicleMockSpec")] = MockEngine()
    vehicle : Annotated[Vehicle, Inject]

    @Test
    def test_start_engine(self):
        result = self.vehicle.start()
        assert result == "Mock Started", "Should have injected mock"
# end::class[]
