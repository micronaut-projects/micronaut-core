package io.micronaut.inject.visitor

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
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
        def definition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

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
        visitedElements.find { it.name == 'deleteAll'}.parameters[0].genericType.getFirstTypeArgument().get().name == 'test.Foo'
        IntroductionVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        visitedElements.size() == 5
        visitedElements[1].name == 'save'
        visitedElements[1].genericReturnType.name == 'test.Foo'
        visitedElements[1].parameters[0].genericType.name == 'test.Foo'
        visitedElements[2].parameters[0].genericType.name == Iterable.name
        visitedElements[2].parameters[0].genericType.getFirstTypeArgument().isPresent()
        visitedElements[2].parameters[0].genericType.getFirstTypeArgument().get().name == 'test.Foo'
        visitedElements[2].genericReturnType.getFirstTypeArgument().get().name == 'test.Foo'

        and:
        ClassElement classElement = IntroductionVisitor.VISITED_CLASS_ELEMENTS[0]
        classElement.getTypeArguments(InterfaceWithGenerics).size() == 2
        classElement.getTypeArguments(InterfaceWithGenerics).get("ET").name == 'test.Foo'
        classElement.getTypeArguments(InterfaceWithGenerics).get("ID").name == Long.name

        and:
        def saveMethod = definition.findPossibleMethods("save").findFirst().get()
        saveMethod.getReturnType().type.name == 'test.Foo'
        saveMethod.getArguments()[0].type.name == 'test.Foo'
        def saveAllMethod = definition.findPossibleMethods("saveAll").findFirst().get()
        saveAllMethod.getArguments()[0].getFirstTypeVariable().get().type.name == 'test.Foo'
        saveAllMethod.getReturnType().getFirstTypeVariable().get().type.name == 'test.Foo'


        and:"A return type that has type arguments has the correct types"
        def findMethod = definition.findPossibleMethods("find").findFirst().get()
        findMethod.getReturnType().getFirstTypeVariable().get().type.name == 'test.Foo'
        findMethod.getArguments()[0].type == Long

        and:"A method that uses wild card types that extend generic types has the correct types"
        def deleteMethod = definition.findPossibleMethods("deleteAll").findFirst().get()
        deleteMethod.arguments[0].type == Iterable
        deleteMethod.arguments[0].firstTypeVariable.get().type.name == 'test.Foo'
    }

    void "test that it is possible to visit introduction advice that extend from existing interfaces with inheritance"() {
        def definition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

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
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[1].name == 'save'
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[1].genericReturnType.name == 'test.Foo'
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[1].parameters[0].genericType.name == 'test.Foo'
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[2].parameters[0].genericType.name == Iterable.name
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[2].parameters[0].genericType.getFirstTypeArgument().isPresent()
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[2].parameters[0].genericType.getFirstTypeArgument().get().name == 'test.Foo'
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[2].genericReturnType.getFirstTypeArgument().get().name == 'test.Foo'

        and:
        ClassElement classElement = IntroductionVisitor.VISITED_CLASS_ELEMENTS[0]
        classElement.getTypeArguments(InterfaceWithGenerics).size() == 2
        classElement.getTypeArguments(InterfaceWithGenerics).get("ET").name == 'test.Foo'
        classElement.getTypeArguments(InterfaceWithGenerics).get("ID").name == Long.name

        and:
        def saveMethod = definition.findPossibleMethods("save").findFirst().get()
        saveMethod.getReturnType().type.name == 'test.Foo'
        saveMethod.getArguments()[0].type.name == 'test.Foo'
        def saveAllMethod = definition.findPossibleMethods("saveAll").findFirst().get()
        saveAllMethod.getArguments()[0].getFirstTypeVariable().get().type.name == 'test.Foo'
        saveAllMethod.getReturnType().getFirstTypeVariable().get().type.name == 'test.Foo'


        and:"A return type that has type arguments has the correct types"
        def findMethod = definition.findPossibleMethods("find").findFirst().get()
        findMethod.getReturnType().getFirstTypeVariable().get().type.name == 'test.Foo'
        findMethod.getArguments()[0].type == Long

        and:"A method that uses wild card types that extend generic types has the correct types"
        def deleteMethod = definition.findPossibleMethods("deleteAll").findFirst().get()
        deleteMethod.arguments[0].type == Iterable
        deleteMethod.arguments[0].firstTypeVariable.get().type.name == 'test.Foo'
    }
}
