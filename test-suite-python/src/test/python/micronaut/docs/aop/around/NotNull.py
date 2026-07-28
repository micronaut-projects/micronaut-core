from micronaut.context.annotation import Executable
# tag::imports[]
from micronaut.aop import Around
# end::imports[]

@Executable
# tag::annotation[]
@Around
def NotNull(func):
    return func
# end::annotation[]
