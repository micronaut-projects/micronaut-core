
from micronaut.context.annotation import EachProperty

# tag::class[]
@EachProperty("engines")
class EngineConfiguration:
    cylinders : int
    enabled : bool = True
# end::class[]
