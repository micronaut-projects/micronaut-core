package io.micronaut.python.annotation.processing.test

import io.micronaut.core.annotation.Blocking
import io.micronaut.context.event.ApplicationEventListener
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.graalvm.polyglot.Value
import spock.lang.PendingFeature

class IntroductionAdviceSpec extends AbstractPythonTypeElementSpec {
    void "test introduction advice implements additional Java interface"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from typing import Protocol
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction(interfaces="java.lang.Runnable")
def RunnableAdvice(cls):
    return cls

@InterceptorBean(RunnableAdvice)
@Singleton
class RunnableAdviceInterceptor(MethodInterceptor):
    runs: int = 0

    def intercept(self, context: MethodInvocationContext):
        if context.getMethodName() == "run":
            self.runs += 1
            return None
        if context.getMethodName() == "get_bar":
            return "introduced"
        return context.proceed()

@RunnableAdvice
@Singleton
class ConcreteRunnableBean:
    @Executable
    def get_foo(self) -> str:
        return "good"

@RunnableAdvice
@Singleton
class AbstractRunnableBean(ABC):
    @Executable
    def get_foo(self) -> str:
        return "good"

    @abstractmethod
    def get_bar(self) -> str:
        pass

@RunnableAdvice
class RunnableProtocol(Protocol):
    def get_bar(self) -> str:
        ...
'''

        when:
        def context = buildContext(pythonCode)
        def concreteDefinition = getBeanDefinition(context, "python.ConcreteRunnableBean")
        def abstractDefinition = getBeanDefinition(context, "python.AbstractRunnableBean")
        def protocolDefinition = getBeanDefinition(context, "python.RunnableProtocol")
        Value concrete = getBean(context, "python.ConcreteRunnableBean").asPolyglotValue()
        Value abstractBean = getBean(context, "python.AbstractRunnableBean").asPolyglotValue()
        Value protocol = getBean(context, "python.RunnableProtocol").asPolyglotValue()
        def interceptor = getBean(context, "python.RunnableAdviceInterceptor")

        then:
        Runnable.isAssignableFrom(concreteDefinition.beanType)
        Runnable.isAssignableFrom(abstractDefinition.beanType)
        Runnable.isAssignableFrom(protocolDefinition.beanType)
        concreteDefinition.executableMethods*.methodName.containsAll(["get_foo", "run"])
        abstractDefinition.executableMethods*.methodName.containsAll(["get_foo", "get_bar", "run"])
        protocolDefinition.executableMethods*.methodName.containsAll(["get_bar", "run"])

        concrete.invokeMember("get_foo").asString() == "good"
        abstractBean.invokeMember("get_foo").asString() == "good"
        abstractBean.invokeMember("get_bar").asString() == "introduced"
        protocol.invokeMember("get_bar").asString() == "introduced"
        concrete.invokeMember("run").isNull()
        abstractBean.invokeMember("run").isNull()
        protocol.invokeMember("run").isNull()
        interceptor.runs == 3

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0068")
    void "test introduction advice implements additional generic event listener interface"() {
        given:
        def pythonCode = '''
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from micronaut.context.event import ApplicationEventListener
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction(interfaces=ApplicationEventListener)
def ListenerAdvice(cls):
    return cls

@InterceptorBean(ListenerAdvice)
@Singleton
class ListenerAdviceInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        if context.getMethodName() == "onApplicationEvent":
            return None
        return context.proceed()

@ListenerAdvice
@Singleton
class ConcreteListenerBean:
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.ConcreteListenerBean")
        Value bean = getBean(context, "python.ConcreteListenerBean").asPolyglotValue()

        then:
        ApplicationEventListener.isAssignableFrom(definition.beanType)
        definition.executableMethods*.methodName.contains("onApplicationEvent")
        bean.invokeMember("onApplicationEvent", new Object()).isNull()

        cleanup:
        context?.close()
    }

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

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0062")
    void "test type level around advice on introduced abstract methods mutates arguments"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext, Introduction
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Introduction
def Stub(cls):
    return cls

@Around
def Mutating(cls):
    return cls

@InterceptorBean(Stub)
@Singleton
class StubIntroduction(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        for param in context.getParameters().values():
            return param.getValue()
        return None

@InterceptorBean(Mutating)
@Singleton
class MutatingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        for param in context.getParameters().values():
            if param.getName() == "name":
                param.setValue("changed")
        return context.proceed()

@Stub
@Mutating
@Singleton
class InterfaceIntroductionClass(ABC):
    @abstractmethod
    def test(self, name: str) -> str:
        pass

    @abstractmethod
    def test_with_age(self, name: str, age: int) -> str:
        pass
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.InterfaceIntroductionClass")
        def value = bean.asPolyglotValue()

        then:
        value.invokeMember("test", "test").asString() == "changed"
        value.invokeMember("test_with_age", "test", 10).asString() == "changed"

        cleanup:
        context?.close()
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0061")
    void "test type level around advice on introduced inherited generic abstract method"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from typing import Generic, TypeVar
from micronaut.aop import Around, InterceptorBean, MethodInvocationContext, Introduction
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
T = TypeVar("T")

@Introduction
def Stub(cls):
    return cls

@Around
def Mutating(cls):
    return cls

@InterceptorBean(Stub)
@Singleton
class StubIntroduction(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        for param in context.getParameters().values():
            return param.getValue()
        return None

@InterceptorBean(Mutating)
@Singleton
class MutatingInterceptor(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        for param in context.getParameters().values():
            if param.getName() == "name":
                param.setValue("changed")
        return context.proceed()

class SuperInterface(Generic[T], ABC):
    @abstractmethod
    def test_generics_from_type(self, name: T, age: int) -> T:
        pass

@Stub
@Mutating
@Singleton
class InterfaceIntroductionClass(SuperInterface[str], ABC):
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.InterfaceIntroductionClass").asPolyglotValue()

        then:
        bean.invokeMember("test_generics_from_type", "test", 10).asString() == "changed"

        cleanup:
        context?.close()
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
from micronaut.core.annotation import Blocking
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
    @Blocking
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
        saveTwo.hasAnnotation(Blocking)
        saveTwo.annotationMetadata.declaredAnnotationNames.contains(Blocking.name)

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
