# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from .Car import Car
# end::imports[]

# tag::class[]
@Singleton
@Requires(classes = Car)
def Driver(func):
    return func
# end::class[]
