# tag::imports[]
from jakarta.inject import Singleton
from .ColorPicker import ColorPicker
# end::imports[]

# tag::clazz[]
@Singleton
class Blue(ColorPicker):

    def color(self) -> str:
        return "blue"
# end::clazz[]
