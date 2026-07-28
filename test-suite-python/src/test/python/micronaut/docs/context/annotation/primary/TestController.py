
from jakarta.inject import Singleton
from micronaut.http.annotation import Controller, Get
from .ColorPicker import ColorPicker
from micronaut.context.annotation import Requires

@Requires(property = 'spec.name', value = 'primaryspec')
# tag::clazz[]
@Controller("/test")
class TestController:
    def __init__(self, colorPicker: ColorPicker):  # <1>
        self.colorPicker = colorPicker

    @Get
    def index(self) -> str:
        return colorPicker.color()
# end::clazz[]
