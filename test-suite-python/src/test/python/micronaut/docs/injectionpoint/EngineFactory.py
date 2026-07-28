from micronaut.context.annotation import Factory, Prototype
from micronaut.inject import InjectionPoint
from .Engine import Engine
from .V6Engine import V6Engine
from .V8Engine import V8Engine
from .Cylinders import Cylinders

# tag::class[]
@Factory
class EngineFactory:
    @Prototype
    def build_engine(self, injection_point : InjectionPoint) -> Engine: # <1>
        cylinders = (
            injection_point
                .getAnnotationMetadata()
                .intValue("micronaut.docs.injectionpoint.Cylinders", 'value')
                .orElse(8)) # <2>
        match cylinders: # <3>
            case 8:
                return V8Engine()
            case 6:
                return V6Engine()
            case _:
                raise Exception(f"Unsupported number of cylinders specified: {cylinders}")

# end::class[]
