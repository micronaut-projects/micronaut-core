from micronaut.aop import Around
from micronaut.context.annotation import Executable


@Executable
@Around
def Labelled(func):
    return func
