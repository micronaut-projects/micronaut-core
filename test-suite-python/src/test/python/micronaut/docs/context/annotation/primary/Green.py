# tag::imports[]
from micronaut.context.annotation import Primary
from jakarta.inject import Singleton
from .ColorPicker import ColorPicker
# end::imports[]

# tag::clazz[]
@Primary
@Singleton
class Green(ColorPicker):

    def color(self) -> str:
        return "green"
# end::clazz[]
