# tag::imports[]
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.context import BeanProvider
from micronaut.context.annotation import Any
from micronaut.docs.qualifiers.annotationmember.Engine import Engine
# end::imports[]


# tag::clazz[]
@Singleton
class Vehicle:
    def __init__(self, engine_provider: Annotated[BeanProvider[Engine], Any]):  # <1>
        self.engine_provider = engine_provider

    def start(self) -> None:
        if self.engine_provider.isPresent():
            self.engine_provider.get().start()  # <2>
# end::clazz[]

    # tag::startAll[]
    def start_all(self) -> None:
        if self.engine_provider.isPresent():  # <1>
            for engine in self.engine_provider.stream().toList():
                engine.start()  # <2>
    # end::startAll[]
