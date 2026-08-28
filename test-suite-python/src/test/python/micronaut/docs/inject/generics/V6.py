# tag::class[]
from .CylinderProvider import CylinderProvider

class V6(CylinderProvider):
    def get_cylinders(self) -> int:
        return 6
# end::class[]
