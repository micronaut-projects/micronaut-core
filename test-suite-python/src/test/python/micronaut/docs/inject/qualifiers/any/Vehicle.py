from micronaut.docs.inject.qualifiers.named import Engine

# tag::imports[]
from micronaut.context import BeanProvider
from io.micronaut.context.annotation import Any
from jakarta.inject import Singleton
# end::imports[]

# tag::clazz[]
@Singleton
class Vehicle:
    def __init__(self, engine_provider: BeanProvider[Engine]):  # <1>
        self.engine_provider = engine_provider
# end::clazz[]
    # tag::startAll[]
    def startAll(self):
        if self.engine_provider.isPresent():
            for engine in self.engine_provider:
                engine.start()

    # end::startAll[]
