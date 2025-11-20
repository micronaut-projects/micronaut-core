# tag::imports[]
from jakarta.inject import Qualifier
# end::imports[]

# tag::class[]
@Qualifier
def V8(func):
    return func
# end::class[]
