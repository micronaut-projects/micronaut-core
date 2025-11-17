from jakarta.inject import Singleton
from .Engine import Engine
from .V6 import V6

# tag::class[]
@Singleton # <3>
class V6Engine(Engine[V6]):
    def get_cylinder_provider(self) -> V6:
        return V6()
# end::class[]
