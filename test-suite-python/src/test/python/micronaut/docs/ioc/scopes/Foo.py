from micronaut.context.annotation import Prototype
from .Driver import Driver

@Driver
@Prototype
class Foo:
    pass
