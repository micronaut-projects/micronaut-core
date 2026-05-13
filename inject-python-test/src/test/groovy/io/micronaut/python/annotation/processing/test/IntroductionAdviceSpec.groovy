package io.micronaut.python.annotation.processing.test

class IntroductionAdviceSpec extends AbstractPythonTypeElementSpec {
    void "test introduction advice with the decorator defined in Python and invoked from another type"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
from abc import ABC, abstractmethod
from datetime import datetime
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def Stub(value : str = ""):
    def class_decorator(cls):
        return cls
    return class_decorator

@InterceptorBean(Stub.__qualname__)
@Singleton
class StubIntroduction(MethodInterceptor):
    invoked: int = 0

    def intercept(self, context : MethodInvocationContext):
        self.invoked += 1
        return context.getValue("python.Stub", context.getReturnType().getType()).orElse(None)


@Stub()
class StubExample(ABC):
    @abstractmethod
    @Stub("10")
    def get_number(self) -> int:
        pass

    @abstractmethod
    def get_date(self) -> datetime:
        pass

@Singleton
class TestCaller:
    def __init__(self, test : StubExample):
        self.test = test

    @Executable
    def get_number(self) -> int:
        return self.test.get_number()

    @Executable
    def get_date(self) -> datetime:
        return self.test.get_date()

'''
        when:
        def context = buildContext(pythonCode)
        def testBean = getBean(context, "python.TestCaller")
        def stub = getBean(context, "python.StubExample").asPolyglotValue()
        def interceptor = getBean(context, "python.StubIntroduction")

        then:
        stub.invokeMember("get_number").asInt() == 10
        testBean.asPolyglotValue().invokeMember("get_number").asInt() == 10
        testBean.get_number() == 10
        testBean.get_date() == null
        interceptor.invoked == 4
    }

    void "test introduction advice runs around interceptors first"() {
        given:
        def pythonCode = '''
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext, Introduction
from jakarta.inject import Singleton
from abc import ABC, abstractmethod
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def Stub(value: str = ""):
    def class_decorator(cls):
        return cls
    return class_decorator

@Around
def Guard(func):
    return func

@InterceptorBean(Stub.__qualname__)
@Singleton
class StubIntroduction(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return context.getValue("python.Stub", context.getReturnType().getType()).orElse(None)

@InterceptorBean(Guard.__qualname__)
@Singleton
class GuardInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        raise RuntimeError("around interceptor executed")

@Stub()
class StubExample(ABC):
    @abstractmethod
    @Guard
    @Stub("10")
    def get_number(self) -> int:
        pass
'''

        when:
        def context = buildContext(pythonCode)
        getBean(context, "python.StubExample").asPolyglotValue().invokeMember("get_number")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("around interceptor executed")

        cleanup:
        context?.close()
    }
}
