package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class IntroductionVisitorSpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, AllElementsVisitor.name)
    }

    def cleanup() {
        AllElementsVisitor.clearVisited()
    }

    void "test that it is possible to visit introduction advice that extend from existing interfaces"() {
        def definition = buildBeanDefinition('introv1.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package introv1;

import io.micronaut.aop.introduction.Stub;
import io.micronaut.inject.visitor.InterfaceWithGenerics;

@Stub
interface MyInterface extends InterfaceWithGenerics<Foo, Long>  {
    String myMethod();
}

class Foo {}
''')
        def visitedElements = IntroductionVisitor.VISITED_METHOD_ELEMENTS
        expect:
        visitedElements.find { it.name == 'deleteAll'}.parameters[0].genericType.getFirstTypeArgument().get().name == 'introv1.Foo'
        IntroductionVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        visitedElements.size() == 5
        def saveElement = visitedElements.find { it.name == 'save'}
        saveElement.name == 'save'
        saveElement.genericReturnType.name == 'introv1.Foo'
        saveElement.parameters[0].genericType.name == 'introv1.Foo'
        def saveAllElement = visitedElements.find { it.name == 'saveAll'}
        saveAllElement.parameters[0].genericType.name == Iterable.name
        saveAllElement.parameters[0].genericType.getFirstTypeArgument().isPresent()
        saveAllElement.parameters[0].genericType.getFirstTypeArgument().get().name == 'introv1.Foo'
        saveAllElement.genericReturnType.getFirstTypeArgument().get().name == 'introv1.Foo'

        and:
        ClassElement classElement = IntroductionVisitor.VISITED_CLASS_ELEMENTS[0]
        classElement.getTypeArguments(InterfaceWithGenerics).size() == 2
        classElement.getTypeArguments(InterfaceWithGenerics).get("ET").name == 'introv1.Foo'
        classElement.getTypeArguments(InterfaceWithGenerics).get("ID").name == Long.name

        and:
        def saveMethod = definition.findPossibleMethods("save").findFirst().get()
        saveMethod.getReturnType().type.name == 'introv1.Foo'
        saveMethod.getArguments()[0].type.name == 'introv1.Foo'
        def saveAllMethod = definition.findPossibleMethods("saveAll").findFirst().get()
        saveAllMethod.getArguments()[0].getFirstTypeVariable().get().type.name == 'introv1.Foo'
        saveAllMethod.getReturnType().getFirstTypeVariable().get().type.name == 'introv1.Foo'


        and:"A return type that has type arguments has the correct types"
        def findMethod = definition.findPossibleMethods("find").findFirst().get()
        findMethod.getReturnType().getFirstTypeVariable().get().type.name == 'introv1.Foo'
        findMethod.getArguments()[0].type == Long

        and:"A method that uses wild card types that extend generic types has the correct types"
        def deleteMethod = definition.findPossibleMethods("deleteAll").findFirst().get()
        deleteMethod.arguments[0].type == Iterable
        deleteMethod.arguments[0].firstTypeVariable.get().type.name == 'introv1.Foo'
    }

    void "test that it is possible to visit introduction advice that extend from existing interfaces with inheritance"() {
        def definition = buildBeanDefinition('introv2.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package introv2;

import io.micronaut.aop.introduction.Stub;
import io.micronaut.inject.visitor.InterfaceWithGenerics;

@Stub
interface MyInterface extends AnotherInterface  {
    String myMethod();
}

interface AnotherInterface extends InterfaceWithGenerics<Foo, Long> {}
class Foo {}
''')
        expect:
        IntroductionVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        IntroductionVisitor.VISITED_METHOD_ELEMENTS.size() == 5
        def saveElement = IntroductionVisitor.VISITED_METHOD_ELEMENTS.find { it.name == 'save'}
        saveElement.genericReturnType.name == 'introv2.Foo'
        saveElement.parameters[0].genericType.name == 'introv2.Foo'
        def saveElementAll = IntroductionVisitor.VISITED_METHOD_ELEMENTS.find { it.name == 'saveAll'}
        saveElementAll.parameters[0].genericType.name == Iterable.name
        saveElementAll.parameters[0].genericType.getFirstTypeArgument().isPresent()
        saveElementAll.parameters[0].genericType.getFirstTypeArgument().get().name == 'introv2.Foo'
        saveElementAll.genericReturnType.getFirstTypeArgument().get().name == 'introv2.Foo'

        and:
        ClassElement classElement = IntroductionVisitor.VISITED_CLASS_ELEMENTS[0]
        classElement.getTypeArguments(InterfaceWithGenerics).size() == 2
        classElement.getTypeArguments(InterfaceWithGenerics).get("ET").name == 'introv2.Foo'
        classElement.getTypeArguments(InterfaceWithGenerics).get("ID").name == Long.name

        and:
        def saveMethod = definition.findPossibleMethods("save").findFirst().get()
        saveMethod.getReturnType().type.name == 'introv2.Foo'
        saveMethod.getArguments()[0].type.name == 'introv2.Foo'
        def saveAllMethod = definition.findPossibleMethods("saveAll").findFirst().get()
        saveAllMethod.getArguments()[0].getFirstTypeVariable().get().type.name == 'introv2.Foo'
        saveAllMethod.getReturnType().getFirstTypeVariable().get().type.name == 'introv2.Foo'


        and:"A return type that has type arguments has the correct types"
        def findMethod = definition.findPossibleMethods("find").findFirst().get()
        findMethod.getReturnType().getFirstTypeVariable().get().type.name == 'introv2.Foo'
        findMethod.getArguments()[0].type == Long

        and:"A method that uses wild card types that extend generic types has the correct types"
        def deleteMethod = definition.findPossibleMethods("deleteAll").findFirst().get()
        deleteMethod.arguments[0].type == Iterable
        deleteMethod.arguments[0].firstTypeVariable.get().type.name == 'introv2.Foo'
    }
}
