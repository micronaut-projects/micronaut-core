from .CrankShaft import CrankShaft
from .Engine import Engine

# tag::class[]
class V8Engine(Engine):
    def __init__(self, crank_shaft: CrankShaft):
        self.crank_shaft = crank_shaft
        self.cylinders = 8

    def start(self) -> str:
        return "Starting V8"
# end::class[]
