package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.annotate.MyAnnotation
import io.micronaut.python.annotation.processing.test.repository.VisitorGenericRepository
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

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0082")
    void "test visitor sees generic types on introduced Java interface methods"() {
        given:
        GenericIntroductionVisitor.ENABLED = true
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.aop import InterceptorBean, Introduction, MethodInvocationContext
from jakarta.inject import Singleton
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")
VisitorGenericRepository = java.type("io.micronaut.python.annotation.processing.test.repository.VisitorGenericRepository")

@dataclass
class MyPerson:
    id: int
    name: str

@Introduction
def RepoDef(cls):
    return cls

@InterceptorBean(RepoDef)
@Singleton
class MyRepoIntroducer(MethodInterceptor):
    def intercept(self, context: MethodInvocationContext):
        return None

@RepoDef
class MyPersonRepository(VisitorGenericRepository[MyPerson, int], new_style=True):
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def definition = getBeanDefinition(context, "python.MyPersonRepository")
        ClassElement repositoryElement = GenericIntroductionVisitor.VISITED_CLASS_ELEMENTS.first()
        def saveElement = GenericIntroductionVisitor.VISITED_METHOD_ELEMENTS.find { it.name == "save" }
        def saveAllElement = GenericIntroductionVisitor.VISITED_METHOD_ELEMENTS.find { it.name == "saveAll" }
        def findElement = GenericIntroductionVisitor.VISITED_METHOD_ELEMENTS.find { it.name == "find" }
        def deleteAllElement = GenericIntroductionVisitor.VISITED_METHOD_ELEMENTS.find { it.name == "deleteAll" }

        then:
        definition != null
        repositoryElement.getTypeArguments(VisitorGenericRepository).get("ET").name == "python.MyPerson"
        repositoryElement.getTypeArguments(VisitorGenericRepository).get("ID").name in [Integer.name, Integer.TYPE.name]

        and:
        saveElement.genericReturnType.name == "python.MyPerson"
        saveElement.parameters[0].genericType.name == "python.MyPerson"
        saveAllElement.genericReturnType.firstTypeArgument.get().name == "python.MyPerson"
        saveAllElement.parameters[0].genericType.firstTypeArgument.get().name == "python.MyPerson"
        findElement.genericReturnType.firstTypeArgument.get().name == "python.MyPerson"
        findElement.parameters[0].genericType.name in [Integer.name, Integer.TYPE.name]
        deleteAllElement.parameters[0].genericType.firstTypeArgument.get().name == "python.MyPerson"

        cleanup:
        GenericIntroductionVisitor.reset()
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

    static class GenericIntroductionVisitor implements TypeElementVisitor<Object, Object> {
        static boolean ENABLED = false
        static final Set<String> METHOD_NAMES = ["save", "saveAll", "find", "deleteAll"] as Set
        static final List<ClassElement> VISITED_CLASS_ELEMENTS = []
        static final List<MethodElement> VISITED_METHOD_ELEMENTS = []

        static void reset() {
            ENABLED = false
            VISITED_CLASS_ELEMENTS.clear()
            VISITED_METHOD_ELEMENTS.clear()
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (ENABLED && element.simpleName == "MyPersonRepository") {
                VISITED_CLASS_ELEMENTS.add(element)
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (ENABLED && METHOD_NAMES.contains(element.name)) {
                VISITED_METHOD_ELEMENTS.add(element)
            }
        }
    }
}
