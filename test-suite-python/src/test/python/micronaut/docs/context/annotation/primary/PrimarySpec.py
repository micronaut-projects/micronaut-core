from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context import ApplicationContext
from jakarta.inject import Inject
from typing import Annotated

from .ColorPicker import ColorPicker

@MicronautTest
class PrimarySpec:
    context: Annotated[ApplicationContext, Inject] = None

    @Test
    def test_primary_color_picker_is_injected(self):
        # tag::primary[]
        color_picker = self.context.getBean(ColorPicker)
        print(color_picker.color())
        # end::primary[]

        assert "green" == color_picker.color()

    @Test
    def test_both_color_pickers_are_available(self):
        beans = self.context.getBeansOfType(ColorPicker)
        assert 2 == len(beans)
