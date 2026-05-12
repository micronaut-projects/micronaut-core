# tag::clazz[]
from abc import ABC, abstractmethod

class ColorPicker(ABC):
    """
    Interface for color picker implementations.
    """

    @abstractmethod
    def color(self) -> str:
        """
        Returns the color value.
        """
        ...
# end::clazz[]
