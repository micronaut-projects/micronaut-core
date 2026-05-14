package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.annotate.MyAnnotation
import spock.lang.PendingFeature

class IntroducedBeanVisitorSpec extends AbstractPythonTypeElementSpec {

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0072")
    void "test visitor annotations are retained on introduced methods"() {
        given:
        IntroducedMethodVisitor.ENABLED = true
        def pythonCode = '''
from abc import ABC, abstractmethod
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

class MyBean:
    name: str

@Introduction
def RepoDef(cls):
    return cls

@InterceptorBean(RepoDef)
@Singleton
class MyRepoIntroducer(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return None

class Repo1(ABC):
    @abstractmethod
    def find_all(self) -> list[MyBean]:
        pass

    @abstractmethod
    def method1(self) -> list[MyBean]:
        pass

class Repo2(ABC):
    @abstractmethod
    def find_all(self) -> list[MyBean]:
        pass

    @abstractmethod
    def method2(self) -> list[MyBean]:
        pass

@RepoDef
class Repo3(Repo2, Repo1):
    @abstractmethod
    def method3(self) -> list[MyBean]:
        pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.Repo3")
        def findAll = definition.getRequiredMethod("find_all")
        def method1 = definition.getRequiredMethod("method1")
        def method2 = definition.getRequiredMethod("method2")
        def method3 = definition.getRequiredMethod("method3")

        then:
        findAll.hasAnnotation(MyAnnotation)
        method1.hasAnnotation(MyAnnotation)
        method2.hasAnnotation(MyAnnotation)
        method3.hasAnnotation(MyAnnotation)

        cleanup:
        IntroducedMethodVisitor.ENABLED = false
        context?.close()
    }

    static class IntroducedMethodVisitor implements TypeElementVisitor<Object, Object> {
        static boolean ENABLED = false
        static final Set<String> METHOD_NAMES = ["find_all", "method1", "method2", "method3"] as Set

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (ENABLED && METHOD_NAMES.contains(element.name)) {
                element.annotate(MyAnnotation)
            }
        }
    }
}
