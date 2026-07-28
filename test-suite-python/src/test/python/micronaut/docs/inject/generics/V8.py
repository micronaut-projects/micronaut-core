# tag::class[]
from .CylinderProvider import CylinderProvider

class V8(CylinderProvider):
    def get_cylinders(self) -> int:
        return 8
# end::class[]
