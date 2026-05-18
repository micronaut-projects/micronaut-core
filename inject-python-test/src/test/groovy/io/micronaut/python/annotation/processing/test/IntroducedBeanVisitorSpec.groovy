package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.annotate.MyAnnotation
import io.micronaut.python.annotation.processing.test.repository.VisitorGenericRepository

class IntroducedBeanVisitorSpec extends AbstractPythonTypeElementSpec {

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

        then:
        definition != null
        GenericIntroductionVisitor.REPOSITORY_TYPE_ARGUMENTS["ET"] == "python.MyPerson"
        GenericIntroductionVisitor.REPOSITORY_TYPE_ARGUMENTS["ID"] in [Integer.name, Integer.TYPE.name]

        and:
        GenericIntroductionVisitor.METHOD_TYPES["save"].returnType == "python.MyPerson"
        GenericIntroductionVisitor.METHOD_TYPES["save"].parameterTypes == ["python.MyPerson"]
        GenericIntroductionVisitor.METHOD_TYPES["saveAll"].returnTypeArgument == "python.MyPerson"
        GenericIntroductionVisitor.METHOD_TYPES["saveAll"].parameterTypeArguments == ["python.MyPerson"]
        GenericIntroductionVisitor.METHOD_TYPES["find"].returnTypeArgument == "python.MyPerson"
        GenericIntroductionVisitor.METHOD_TYPES["find"].parameterTypes[0] in [Integer.name, Integer.TYPE.name]
        GenericIntroductionVisitor.METHOD_TYPES["deleteAll"].parameterTypeArguments == ["python.MyPerson"]

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
        static final Map<String, String> REPOSITORY_TYPE_ARGUMENTS = [:]
        static final Map<String, Map<String, Object>> METHOD_TYPES = [:]

        static void reset() {
            ENABLED = false
            REPOSITORY_TYPE_ARGUMENTS.clear()
            METHOD_TYPES.clear()
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (ENABLED && element.simpleName == "MyPersonRepository") {
                element.getTypeArguments(VisitorGenericRepository).each { name, type ->
                    REPOSITORY_TYPE_ARGUMENTS[name] = type.name
                }
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (ENABLED && METHOD_NAMES.contains(element.name)) {
                def repositoryTypeArguments = element.getTypeArguments()
                if (repositoryTypeArguments["ET"]?.name == "python.MyPerson") {
                    METHOD_TYPES[element.name] = [
                        returnType            : element.genericReturnType.name,
                        returnTypeArgument    : element.genericReturnType.firstTypeArgument.map { it.name }.orElse(null),
                        parameterTypes        : element.parameters.collect { it.genericType.name },
                        parameterTypeArguments: element.parameters.collect { it.genericType.firstTypeArgument.map { arg -> arg.name }.orElse(null) }
                    ]
                }
            }
        }
    }
}
