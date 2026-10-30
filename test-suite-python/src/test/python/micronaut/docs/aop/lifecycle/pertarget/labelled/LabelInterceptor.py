# tag::imports[]
import java

from micronaut.aop import InterceptorBean, MethodInvocationContext
from micronaut.context import InterceptionTarget
from micronaut.context.annotation import Prototype
# end::imports[]

from .Labelled import Labelled

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
Named = java.type("io.micronaut.core.naming.Named")


# tag::interceptor[]
@Prototype
@InterceptorBean(Labelled)
class LabelInterceptor(MethodInterceptor):
    def __init__(self, target: InterceptionTarget):  # <1>
        qualifier = target.definition().getDeclaredQualifier()
        self.label = qualifier.getName() if isinstance(qualifier, Named) \
            else target.definition().getBeanType().getSimpleName()  # <2>

    def intercept(self, context: MethodInvocationContext):
        return f"[{self.label}] {context.proceed()}"
# end::interceptor[]
