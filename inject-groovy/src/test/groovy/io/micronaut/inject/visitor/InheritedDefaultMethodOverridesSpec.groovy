package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery

class InheritedDefaultMethodOverridesSpec extends AbstractBeanDefinitionSpec {

    void "an inherited interface default method seen through a sub-interface does not override itself"() {
        given:
        ClassElement ce = buildClassElement('''
package test

interface Base {
    default String greet() {
        return "hello"
    }
}

interface Sub extends Base {
}

class Impl implements Sub {
}
''')
        def method = ce.getEnclosedElements(ElementQuery.ALL_METHODS.named("greet")).first()

        expect:
        ce.name == "test.Impl"
        method.declaringType.name == "test.Base"
        method.overriddenMethods.isEmpty()
    }
}
