package io.micronaut.python.annotation.processing.test

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.graalvm.polyglot.Value
import spock.lang.PendingFeature

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

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0060")
    void "test around advice is applied to concrete methods on introduction bean"() {
        given:
        def pythonCode = '''
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext, Introduction
from jakarta.inject import Singleton
from abc import ABC, abstractmethod
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def Stub(cls):
    return cls

@Around
def Mutating(func):
    return func

@InterceptorBean(Stub)
@Singleton
class StubIntroduction(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return context.getValue("python.Stub", context.getReturnType().getType()).orElse(None)

@InterceptorBean(Mutating)
@Singleton
class MutatingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        for param in context.getParameters().values():
            if param.getName() == "name":
                param.setValue("changed")
        return context.proceed()

@Stub
class AbstractBean(ABC):
    @abstractmethod
    def save(self, name: str, age: int) -> None:
        pass

    @abstractmethod
    def save_two(self, name: str) -> None:
        pass

    @Mutating
    def my_concrete(self, name: str) -> str:
        return name
'''

        when:
        def context = buildContext(pythonCode)
        Value bean = getBean(context, "python.AbstractBean").asPolyglotValue()

        then:
        bean.invokeMember("my_concrete", "test").asString() == "changed"

        cleanup:
        context?.close()
    }

    void "test introduction advice retains validation metadata on abstract methods"() {
        given:
        def pythonCode = '''
from typing import Annotated
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
from jakarta.validation.constraints import Min, NotBlank
from abc import ABC, abstractmethod
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def Stub(cls):
    return cls

@InterceptorBean(Stub)
@Singleton
class StubIntroduction(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return None

@Stub
class ValidationIntroducedService(ABC):
    @abstractmethod
    @Executable
    def save(self, name: Annotated[str, NotBlank], age: Annotated[int, Min(1)]) -> None:
        pass

    @abstractmethod
    @Executable
    def save_two(self, name: Annotated[str, Min(1)]) -> None:
        pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = context.getBeanDefinition(context.classLoader.loadClass("python.ValidationIntroducedService"))
        def save = definition.executableMethods.find { it.methodName == "save" }
        def saveTwo = definition.executableMethods.find { it.methodName == "save_two" }

        then:
        save != null
        save.returnType.type == void.class
        save.arguments[0].annotationMetadata.hasAnnotation(NotBlank)
        save.arguments[1].annotationMetadata.hasAnnotation(Min)
        save.arguments[1].annotationMetadata.getValue(Min, Integer).get() == 1
        saveTwo != null
        saveTwo.returnType.type == void.class
        saveTwo.arguments[0].annotationMetadata.hasAnnotation(Min)

        cleanup:
        context?.close()
    }

    void "test introduction advice on abstract class preserves concrete methods"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext, Introduction
from jakarta.inject import Singleton
from abc import ABC, abstractmethod
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def Stub(value: str = ""):
    def class_decorator(cls):
        return cls
    return class_decorator

@InterceptorBean(Stub.__qualname__)
@Singleton
class StubIntroduction(MethodInterceptor):
    invoked: int = 0

    def intercept(self, context: MethodInvocationContext):
        self.invoked += 1
        return context.getValue("python.Stub", context.getReturnType().getType()).orElse(None)

@Stub()
class AbstractBean(ABC):
    @abstractmethod
    def is_abstract(self) -> str:
        pass

    def non_abstract(self) -> str:
        return "good"
'''

        when:
        def context = buildContext(pythonCode)
        Value bean = getBean(context, "python.AbstractBean").asPolyglotValue()
        def interceptor = getBean(context, "python.StubIntroduction")

        then:
        bean.invokeMember("is_abstract").isNull()
        bean.invokeMember("non_abstract").asString() == "good"
        interceptor.invoked == 1

        cleanup:
        context?.close()
    }
}
