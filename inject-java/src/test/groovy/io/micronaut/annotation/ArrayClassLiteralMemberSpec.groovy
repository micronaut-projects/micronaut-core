package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationClassValue

class ArrayClassLiteralMemberSpec extends AbstractTypeElementSpec {

    void "test an array class literal is recorded as a class value, alone and in an array member"() {
        given:
        def element = buildClassElement('''
package test;

import java.lang.annotation.*;
import java.util.List;

@Types(type = String[].class, primitiveType = int[].class, nested = int[][].class, types = {String[].class, int[].class, List[][].class, String.class, long.class})
class Test {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Types {
    Class<?> type();
    Class<?> primitiveType();
    Class<?> nested();
    Class<?>[] types();
}
''')
        def annotation = element.getAnnotation('test.Types')

        expect:
        annotation.getValues().get('type') == new AnnotationClassValue<>('[Ljava.lang.String;')
        annotation.getValues().get('primitiveType') == new AnnotationClassValue<>('[I')
        annotation.getValues().get('nested') == new AnnotationClassValue<>('[[I')
        (annotation.getValues().get('types') as AnnotationClassValue[])*.name == ['[Ljava.lang.String;', '[I', '[[Ljava.util.List;', 'java.lang.String', 'long']

        and: "the names are the ones Class.getName() gives"
        Class.forName(annotation.getValues().get('type').name) == String[]
        Class.forName(annotation.getValues().get('nested').name) == int[][]
    }

    void "test the declared generic placeholders of a parameterized use are the declared variables"() {
        given:
        def element = buildClassElement('''
package test;

import java.util.*;

class Test<T> {
    List<String> list;
    Map<String, Integer> map;
    T variable;
}
''')

        expect:
        element.getDeclaredGenericPlaceholders()*.variableName == ['T']
        element.getFields().find { it.name == 'list' }.getType().getDeclaredGenericPlaceholders()*.variableName == ['E']
        element.getFields().find { it.name == 'map' }.getType().getDeclaredGenericPlaceholders()*.variableName == ['K', 'V']
        element.getFields().find { it.name == 'list' }.getType().getBoundGenericTypes()*.name == ['java.lang.String']
    }
}
