# tag::imports[]
from jakarta.inject import Singleton
from docs.javabases import AbstractGreeter
# end::imports[]


# tag::class[]
@Singleton
class PythonGreeter(AbstractGreeter):
    def __init__(self):
        super().__init__("Hello")  # <1>

    def name(self) -> str:  # <2>
        return "Python"

    def greet(self) -> str:  # <3>
        return super().greet().upper()

    def greet_twice(self) -> str:  # <4>
        return self.greet() + " " + self.greet()
# end::class[]
