# tag::imports[]
import java

from micronaut.aop import AroundConstruct, InterceptorBinding
from micronaut.context.annotation import Prototype
# end::imports[]

InterceptorKind = java.type("io.micronaut.aop.InterceptorKind")


# tag::class[]
@AroundConstruct  # <1>
@InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)  # <2>
@InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)  # <3>
@Prototype  # <4>
def ProductBean(func):
    return func
# end::class[]
