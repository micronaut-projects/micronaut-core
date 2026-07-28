# tag::imports[]
from micronaut.aop import InterceptorBean, MethodInvocationContext
from micronaut.context.annotation import Executable
import java
from .NotNull import NotNull
# end::imports[]

# tag::interceptor[]
MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@InterceptorBean(NotNull)
class TestAroundInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
        for param in context.getParameters().values():
            if (param.getValue() is None):
                raise Exception(f"Null parameter [{param.getName()}] is not allowed")
        return context.proceed()
# end::interceptor[]
