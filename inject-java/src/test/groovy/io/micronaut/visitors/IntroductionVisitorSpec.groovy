package io.micronaut.visitors

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.IgnoreIf
import spock.util.environment.Jvm

class IntroductionVisitorSpec extends AbstractTypeElementSpec {

    // Java 9+ doesn't allow resolving elements was the compiler
    // is finished being used so this test cannot be made to work beyond Java 8 the way it is currently written
    @IgnoreIf({ Jvm.current.isJava9Compatible() })
    void "test that it is possible to visit introduction advice that extend from existing interfaces"() {
        given:
        def definition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.Stub;
import io.micronaut.visitors.InterfaceWithGenerics;

@Stub
interface MyInterface extends InterfaceWithGenerics<Foo, Long>  {
    String myMethod();
}

class Foo {}
''')
        expect:
        IntroductionVisitor.VISITED_CLASS_ELEMENTS.size() == 1
        IntroductionVisitor.VISITED_METHOD_ELEMENTS.size() == 5
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[1].name == 'save'
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[1].genericReturnType.name == 'test.Foo'
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[1].parameters[0].genericType.name == 'test.Foo'
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[2].parameters[0].genericType.name == Iterable.name
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[2].genericReturnType.getFirstTypeArgument().get().name == 'test.Foo'
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[2].parameters[0].genericType.getFirstTypeArgument().isPresent()
        IntroductionVisitor.VISITED_METHOD_ELEMENTS[2].parameters[0].genericType.getFirstTypeArgument().get().name == 'test.Foo'

        and:"A method that uses wild card types that extend generic types has the correct types in the visitor"
        def deleteMethodElement = IntroductionVisitor.VISITED_METHOD_ELEMENTS.find{ it.name == 'deleteAll'}
        deleteMethodElement.parameters[0].genericType.name == Iterable.name
        deleteMethodElement.parameters[0].genericType.firstTypeArgument.get().name == 'test.Foo'

        and:"a method with generics that extend other generics has correct types"
        def saveMethod = definition.findPossibleMethods("save").findFirst().get()
        saveMethod.getReturnType().type.name == 'test.Foo'
        saveMethod.getArguments()[0].type.name == 'test.Foo'

        and:"A container type with generics that extend other generics has the correct types"
        def saveAllMethod = definition.findPossibleMethods("saveAll").findFirst().get()
        saveAllMethod.getReturnType().getFirstTypeVariable().get().type.name == 'test.Foo'
        saveAllMethod.getArguments()[0].getFirstTypeVariable().get().type.name == 'test.Foo'

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
