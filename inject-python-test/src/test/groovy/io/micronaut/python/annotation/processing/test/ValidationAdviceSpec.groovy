package io.micronaut.python.annotation.processing.test

import io.micronaut.aop.writer.RuntimeProxyBeanDefinitionWriter
import io.micronaut.inject.BeanDefinition

class ValidationAdviceSpec extends AbstractPythonTypeElementSpec {

    private static final String ANN_VALIDATED = 'io.micronaut.validation.Validated'

    private static final String STUB = '''
from typing import Annotated
from abc import ABC, abstractmethod
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from micronaut.context.annotation import Executable
from jakarta.inject import Singleton
from jakarta.validation.constraints import NotBlank
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
'''

    void "test only the constrained method of an introduction class is validated"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "MyBean" + RuntimeProxyBeanDefinitionWriter.RUNTIME_PROXY_SUFFIX, STUB + '''
@Stub
class MyBean(ABC):
    @abstractmethod
    @Executable
    def constrained(self, name: Annotated[str, NotBlank]) -> None:
        pass

    @abstractmethod
    @Executable
    def not_constrained(self, name: str) -> None:
        pass
''')
        def constrained = definition.executableMethods.find { it.methodName == "constrained" }
        def notConstrained = definition.executableMethods.find { it.methodName == "not_constrained" }

        then:
        constrained.hasAnnotation(ANN_VALIDATED)
        !notConstrained.hasAnnotation(ANN_VALIDATED)
    }

    void "test a constrained method inherited from a super class is validated"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "MyBean" + RuntimeProxyBeanDefinitionWriter.RUNTIME_PROXY_SUFFIX, STUB + '''
class Parent(ABC):
    @abstractmethod
    @Executable
    def constrained(self, name: Annotated[str, NotBlank]) -> None:
        pass

    @abstractmethod
    @Executable
    def not_constrained(self, name: str) -> None:
        pass

@Stub
class MyBean(Parent):
    pass
''')
        def constrained = definition.executableMethods.find { it.methodName == "constrained" }
        def notConstrained = definition.executableMethods.find { it.methodName == "not_constrained" }

        then:
        constrained.hasAnnotation(ANN_VALIDATED)
        !notConstrained.hasAnnotation(ANN_VALIDATED)
    }
}
