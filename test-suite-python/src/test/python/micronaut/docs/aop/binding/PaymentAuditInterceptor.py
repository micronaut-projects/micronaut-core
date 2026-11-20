# tag::imports[]
import java

from jakarta.inject import Singleton
from micronaut.aop import MethodInvocationContext
from io.micronaut.docs.aop.binding import Audit
# end::imports[]


MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")


# tag::interceptor[]
@Singleton
@Audit("security")  # <1>
@Audit("billing")
class PaymentAuditInterceptor(MethodInterceptor):
    def __init__(self):
        self.audited = []

    def intercept(self, context: MethodInvocationContext):
        self.audited.append(str(context.getMethodName()))
        return context.proceed()
# end::interceptor[]
