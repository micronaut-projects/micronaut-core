package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.inject.ast.GenericPlaceholderElement

class GroovyDeclaredGenericPlaceholdersSpec extends AbstractBeanDefinitionSpec {

    void "test the declared generic placeholders of a parameterized use are the declared variables, not the arguments"() {
        given:
        def element = buildClassElement('test.Test', '''
package test

import java.util.*

class Test<T extends CharSequence> {
    List<String> list
    Map<String, Integer> map
    T variable
    String plain
}
''')
        def list = element.getFields().find { it.name == 'list' }.getType()
        def map = element.getFields().find { it.name == 'map' }.getType()

        expect:
        element.getDeclaredGenericPlaceholders()*.variableName == ['T']
        element.getDeclaredGenericPlaceholders()[0].getBounds()*.name == ['java.lang.CharSequence']

        and: "the arguments of the use are still the bound generic types"
        list.getBoundGenericTypes()*.name == ['java.lang.String']
        map.getBoundGenericTypes()*.name == ['java.lang.String', 'java.lang.Integer']

        and: "the declared variables come from the declaration"
        list.getDeclaredGenericPlaceholders().every { it instanceof GenericPlaceholderElement }
        list.getDeclaredGenericPlaceholders()*.variableName == ['E']
        map.getDeclaredGenericPlaceholders()*.variableName == ['K', 'V']
        element.getFields().find { it.name == 'plain' }.getType().getDeclaredGenericPlaceholders().isEmpty()
    }

    void "test an array class literal member"() {
        given:
        def element = buildClassElement('test.Test', '''
package test

import java.lang.annotation.*

@Types(type = String[], types = [String[], String])
class Test {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Types {
    Class<?> type()
    Class<?>[] types()
}
''')
        def annotation = element.getAnnotation('test.Types')

        expect:
        annotation.getValues().get('type') == new AnnotationClassValue<>('[Ljava.lang.String;')
        (annotation.getValues().get('types') as AnnotationClassValue[])*.name == ['[Ljava.lang.String;', 'java.lang.String']
    }
}
