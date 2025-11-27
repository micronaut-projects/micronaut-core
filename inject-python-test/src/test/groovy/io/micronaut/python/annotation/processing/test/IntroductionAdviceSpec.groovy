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
import javaScheduled

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def Stub(value : str = ""):
    def class_decorator(cls):
        return cls
    return class_decorator

@InterceptorBean(Stub.__qualname__)
@Singleton
class StubIntroduction(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
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

        then:
        stub.invokeMember("get_number").asInt() == 10
        testBean.asPolyglotValue().invokeMember("get_number").asInt() == 10
        testBean.get_number() == 10
        testBean.get_date() == null
    }
}
