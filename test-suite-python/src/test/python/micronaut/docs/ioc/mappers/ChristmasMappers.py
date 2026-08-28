from abc import ABC, abstractmethod

from .ChristmasTypes import ChristmasPresent, Present, PresentPackaging

# tag::imports[]
from micronaut.context.annotation import Mapper
# end::imports[]


# tag::mapper[]
class ChristmasMappers(ABC):
    @Mapper.Mapping(**{"from": "packaging.color", "to": "packaging_color"})
    @Mapper.Mapping(**{"from": "#{packaging.weight + present.weight}", "to": "weight"})
    @Mapper.Mapping(**{"from": "#{'Merry christmas'}", "to": "greeting_card"})
    @abstractmethod
    def merge(self, packaging: PresentPackaging, present: Present) -> ChristmasPresent:
        ...
# end::mapper[]
