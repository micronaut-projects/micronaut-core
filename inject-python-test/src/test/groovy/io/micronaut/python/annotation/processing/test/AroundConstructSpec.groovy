package io.micronaut.python.annotation.processing.test

class AroundConstructSpec extends AbstractPythonTypeElementSpec {

    void "test around construct interceptor is invoked for constructor injection"() {
        given:
        def pythonCode = '''
from micronaut.aop import AroundConstruct, ConstructorInvocationContext, InterceptorBean
from micronaut.context.env import Environment
from jakarta.inject import Singleton
import java

ConstructorInterceptor = java.type("io.micronaut.aop.ConstructorInterceptor")

@AroundConstruct
def Constructed(target):
    return target

@InterceptorBean(Constructed)
@Singleton
class TestConstructInterceptor(ConstructorInterceptor):
    invoked: bool = False
    parameter_count: int = 0

    def intercept(self, context: ConstructorInvocationContext):
        self.invoked = True
        self.parameter_count = len(context.getParameterValues())
        return context.proceed()

@Constructed
@Singleton
class MyBean:
    def __init__(self, environment: Environment):
        self.environment = environment
'''

        when:
        def context = buildContext(pythonCode)
        def interceptor = getBean(context, "python.TestConstructInterceptor")

        then:
        !interceptor.invoked

        when:
        def bean = getBean(context, "python.MyBean")

        then:
        bean != null
        interceptor.invoked
        interceptor.parameter_count == 1

        cleanup:
        context?.close()
    }
}
