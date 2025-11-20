# tag::imports[]
from micronaut.aop import InterceptorBean, MethodInvocationContext
from jakarta.inject import Singleton
import java
from .Stub import Stub
# end::imports[]

# tag::class[]
MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(Stub) # <1>
@Singleton
class StubIntroduction(MethodInterceptor): # <2>
    def intercept(self, context : MethodInvocationContext):
        return context.getValue(Stub.__qualname__, context.getReturnType().getType()).orElse(None) # <3> <4>
# end::class[]
