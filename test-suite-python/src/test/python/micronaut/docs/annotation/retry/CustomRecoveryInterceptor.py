# tag::imports[]
from micronaut.aop import InterceptorBean, MethodInvocationContext
from micronaut.context import BeanContext
from micronaut.context.annotation import Replaces, Requires
from micronaut.retry.annotation import Recoverable
from micronaut.retry.intercept import RecoveryInterceptor
# end::imports[]


@Requires(property="spec.name", value="CustomRecoveryInterceptorSpec")
# tag::class[]
@InterceptorBean(Recoverable)  # <1>
@Replaces(RecoveryInterceptor)  # <2>
class CustomRecoveryInterceptor(RecoveryInterceptor):
    def __init__(self, bean_context: BeanContext):
        super().__init__(bean_context)

    def findFallbackMethod(self, context: MethodInvocationContext):
        # look up the fallback differently, or delegate to the default lookup
        return super().findFallbackMethod(context)
# end::class[]
