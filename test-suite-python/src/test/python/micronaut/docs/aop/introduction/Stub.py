# tag::imports[]
from micronaut.aop import Introduction
from micronaut.context.annotation import Bean
# end::imports[]

# tag::class[]
@Introduction # <1>
@Bean # <2>
def Stub(value : str = ""):
    def decorator(func):
        return func
    return decorator
# end::class[]
