from micronaut.core.annotation import Primary
from jakarta.inject import Singleton
from .ColorPicker import ColorPicker

@Primary
@Singleton
class Green(ColorPicker):

    def color(self) -> str:
        return "green"
