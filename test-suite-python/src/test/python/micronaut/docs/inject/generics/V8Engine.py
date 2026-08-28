from jakarta.inject import Singleton
from .Engine import Engine
from .V8 import V8

# tag::class[]
@Singleton
class V8Engine(Engine[V8]): # <1>
    def get_cylinder_provider(self) -> V8:
        return V8()
    # end::class[]
