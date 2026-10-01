# tag::imports[]
from jakarta.inject import Singleton
from .MaxLength import MaxLength
from .MinLength import MinLength
# end::imports[]

# tag::class[]
@MinLength(3)
@MaxLength(9)
@Singleton
class CodeValidator:
    pass
# end::class[]
