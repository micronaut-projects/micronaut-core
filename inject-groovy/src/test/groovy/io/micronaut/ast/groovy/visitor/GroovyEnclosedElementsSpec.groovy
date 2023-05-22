package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementQuery

class GroovyEnclosedElementsSpec extends AbstractBeanDefinitionSpec {
    void "test find matching constructors using ElementQuery"() {
        given:
        ClassElement classElement = buildClassElement('''
package elementquery;

class Test extends SuperType {
    static {}

    Test() {}

    Test(int i) {}
}

class SuperType {
    static {}

    SuperType() {}

    SuperType(String s) {}
}
''')
        when:
        def constructors = classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS)

        then:
        constructors.size() == 2

        when:
        def allConstructors = classElement.getEnclosedElements(ElementQuery.of(ConstructorElement.class))

        then:
        allConstructors.size() == 4
    }
}
