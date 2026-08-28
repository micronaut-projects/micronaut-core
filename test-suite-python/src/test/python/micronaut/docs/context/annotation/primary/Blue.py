# tag::clazz[]
from jakarta.inject import Singleton
from .ColorPicker import ColorPicker

@Singleton
class Blue(ColorPicker):

    def color(self) -> str:
        return "blue"
# end::clazz[]
