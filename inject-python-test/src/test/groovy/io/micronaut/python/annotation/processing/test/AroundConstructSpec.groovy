package io.micronaut.python.annotation.processing.test

import io.micronaut.aop.Intercepted
import spock.lang.PendingFeature

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

    void "test around construct without around advice does not intercept methods"() {
        given:
        def pythonCode = '''
from micronaut.aop import AroundConstruct, ConstructorInvocationContext, InterceptorBean, MethodInvocationContext
from micronaut.context.env import Environment
from jakarta.inject import Singleton
import java

ConstructorInterceptor = java.type("io.micronaut.aop.ConstructorInterceptor")
MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

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

@InterceptorBean(Constructed)
@Singleton
class TestMethodInterceptor(MethodInterceptor):
    invoked: bool = False

    def intercept(self, context: MethodInvocationContext):
        self.invoked = True
        return "intercepted"

@Constructed
@Singleton
class MyBean:
    def __init__(self, environment: Environment):
        self.environment = environment

    def test(self) -> str:
        return "good"
'''

        when:
        def context = buildContext(pythonCode)
        def constructorInterceptor = getBean(context, "python.TestConstructInterceptor")
        def methodInterceptor = getBean(context, "python.TestMethodInterceptor")

        then:
        !constructorInterceptor.invoked
        !methodInterceptor.invoked

        when:
        def bean = getBean(context, "python.MyBean")

        then:
        !(bean instanceof Intercepted)
        constructorInterceptor.invoked
        constructorInterceptor.parameter_count == 1
        !methodInterceptor.invoked

        when:
        def result = bean.test()

        then:
        result == "good"
        !methodInterceptor.invoked

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0070")
    void "test around construct with around advice separates constructor and method interception"() {
        given:
        def pythonCode = '''
from micronaut.aop import Around, AroundConstruct, ConstructorInvocationContext, InterceptorBean, MethodInvocationContext
from micronaut.context.env import Environment
from jakarta.inject import Singleton
import java

ConstructorInterceptor = java.type("io.micronaut.aop.ConstructorInterceptor")
MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Around
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

@InterceptorBean(Constructed)
@Singleton
class TestMethodInterceptor(MethodInterceptor):
    invoked: bool = False

    def intercept(self, context: MethodInvocationContext):
        self.invoked = True
        return context.proceed() + " intercepted"

@Constructed
@Singleton
class MyBean:
    def __init__(self, environment: Environment):
        self.environment = environment

    def test(self) -> str:
        return "good"
'''

        when:
        def context = buildContext(pythonCode)
        def constructorInterceptor = getBean(context, "python.TestConstructInterceptor")
        def methodInterceptor = getBean(context, "python.TestMethodInterceptor")

        then:
        !constructorInterceptor.invoked
        !methodInterceptor.invoked

        when:
        def bean = getBean(context, "python.MyBean")

        then:
        bean instanceof Intercepted
        constructorInterceptor.invoked
        constructorInterceptor.parameter_count == 1
        !methodInterceptor.invoked

        when:
        constructorInterceptor.invoked = false
        def result = bean.test()

        then:
        result == "good intercepted"
        methodInterceptor.invoked
        !constructorInterceptor.invoked

        cleanup:
        context?.close()
    }
}
