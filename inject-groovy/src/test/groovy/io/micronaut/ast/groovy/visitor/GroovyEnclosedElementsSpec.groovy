package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

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

    void "test methods declared with a leading dollar are visible"() {
        given:
        ClassElement classElement = buildClassElement('elementquery.Test', '''
package elementquery

class Test {

    String work() { "w" }

    final String $work() { "dw" }

    private String $secret() { "s" }

    static String $stat() { "st" }
}
''')
        when:
        def methods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)

        then: "the methods the user declared are all there, whatever their names start with"
        methods*.name as Set == ['work', '$work', '$secret', '$stat'] as Set

        and: "they keep the modifiers they were declared with"
        methods.find { it.name == '$work' }.isFinal()
        !methods.find { it.name == '$work' }.isStatic()
        !methods.find { it.name == '$work' }.isPrivate()
        methods.find { it.name == '$secret' }.isPrivate()
        methods.find { it.name == '$stat' }.isStatic()

        and: "the helpers the Groovy compiler generates are still hidden"
        methods.every { MethodElement m ->
            !['$getStaticMetaClass', '$getCallSiteArray', '$createCallSiteArray',
              'getMetaClass', 'setMetaClass'].contains(m.name)
        }
    }

    void "test compiler generated members stay hidden for traits, properties and closures"() {
        given:
        ClassElement classElement = buildClassElement('elementquery.Test', '''
package elementquery

trait Greeter {
    String greeting = "hi"
    String greet() { "$greeting" }
}

class Test implements Greeter {

    String name

    String run() { [1, 2].collect { it * 2 }.join(",") }

    String $own() { "o" }
}
''')
        when:
        def methods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)*.name as Set

        then: "the user declared methods, the property accessors and the trait methods are visible"
        methods.contains('run')
        methods.contains('$own')
        methods.contains('getName')
        methods.contains('setName')
        methods.contains('greet')

        and: "the synthetic helpers and trait bridges are not"
        !methods.contains('$getStaticMetaClass')
        !methods.contains('$getCallSiteArray')
        !methods.contains('getMetaClass')
        !methods.contains('setMetaClass')
        methods.every { !it.startsWith('super$') }
        methods.every { !it.contains('trait$') }
    }
}
