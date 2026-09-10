# tag::imports[]
from micronaut.core.annotation import Retainable
# end::imports[]

# tag::class[]
@Retainable  # <1>
def Limit(min: int = 0, max: int = 100):
    def decorator(func):
        return func

    return decorator
# end::class[]
