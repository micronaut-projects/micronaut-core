from micronaut.aop import Around
from micronaut.context.annotation import Executable


@Executable
@Around
def Timed(func):
    return func
