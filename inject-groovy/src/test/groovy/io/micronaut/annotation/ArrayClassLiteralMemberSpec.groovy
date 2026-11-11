package io.micronaut.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.TypeHint
import io.micronaut.inject.BeanDefinition

class ArrayClassLiteralMemberSpec extends AbstractBeanDefinitionSpec {

    void "test an array class literal is recorded as a class value, alone and in an array member"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('arrayclassliteral.Test', '''
package arrayclassliteral

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Types(type = String[].class, primitiveType = int[].class, nested = int[][].class,
    types = [String[].class, int[].class, List[][].class, String.class, long.class],
    primitiveArrays = [boolean[].class, byte[].class, short[].class, int[].class,
        long[].class, char[].class, float[].class, double[].class],
    declaredHere = [Foo[].class, Foo[][].class, Foo.Inner[].class, Foo.class, int[][][].class])
class Test {
}

class Foo {
    static class Inner {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface Types {
    Class<?> type()
    Class<?> primitiveType()
    Class<?> nested()
    Class<?>[] types()
    Class<?>[] primitiveArrays()
    Class<?>[] declaredHere()
}
''')
        def values = metadata.getAnnotation('arrayclassliteral.Types').getValues()

        expect: "a single valued member is named the way Class.getName() names it"
        values.get('type') == new AnnotationClassValue<>('[Ljava.lang.String;')
        values.get('primitiveType') == new AnnotationClassValue<>('[I')
        values.get('nested') == new AnnotationClassValue<>('[[I')

        and: "an array member names every element the same way, arrays and non arrays alike"
        (values.get('types') as AnnotationClassValue[])*.name == ['[Ljava.lang.String;', '[I', '[[Ljava.util.List;', 'java.lang.String', 'long']

        and: "every primitive array is named by its descriptor"
        (values.get('primitiveArrays') as AnnotationClassValue[])*.name == ['[Z', '[B', '[S', '[I', '[J', '[C', '[F', '[D']

        and: "so is an array of a class of the same compilation"
        (values.get('declaredHere') as AnnotationClassValue[])*.name == ['[Larrayclassliteral.Foo;', '[[Larrayclassliteral.Foo;', '[Larrayclassliteral.Foo$Inner;', 'arrayclassliteral.Foo', '[[[I']

        and: "the names are the ones Class.getName() gives"
        Class.forName(values.get('type').name) == String[]
        Class.forName(values.get('nested').name) == int[][]
    }

    void "test array class literals in the defaults of an annotation read back from its class file"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('arrayclassliteraldefaults.Test', '''
package arrayclassliteraldefaults

import io.micronaut.annotation.ArrayDefaults

@ArrayDefaults
class Test {
}
''')
        def defaults = metadata.getDefaultValues(ArrayDefaults.name)

        expect:
        defaults['one'] == new AnnotationClassValue<>('[I')
        (defaults['many'] as AnnotationClassValue[])*.name == ['[I', '[[Ljava.lang.String;', '[[Ljava.util.UUID;', 'java.lang.String']
    }

    void "test a loaded definition holds the array class literals of an array member"() {
        given:
        BeanDefinition definition = buildBeanDefinition('arrayclassliteralloaded.Loaded', '''
package arrayclassliteralloaded

import io.micronaut.core.annotation.TypeHint
import jakarta.inject.Singleton

@Singleton
@TypeHint([int[].class, byte[].class, int[][].class, String[].class, String[][].class,
        java.util.UUID[].class, java.util.UUID[][].class])
class Loaded {
}
''')

        def expected = [int[], byte[], int[][], String[], String[][], UUID[], UUID[][]]

        expect:
        definition.stringValues(TypeHint) as List == expected*.name
        definition.classValues(TypeHint) as List == expected
    }

    void "test a bean required on an array class is enabled"() {
        given:
        def context = buildContext('''
package arrayclassliteralrequires

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@Singleton
@Requires(classes = [int[].class])
class InList {
}

@Singleton
@Requires(classes = int[].class)
class Single {
}

@Singleton
@Requires(classes = [int[][].class, java.util.UUID[][].class])
class Nested {
}
''')

        expect:
        getBean(context, 'arrayclassliteralrequires.InList')
        getBean(context, 'arrayclassliteralrequires.Single')
        getBean(context, 'arrayclassliteralrequires.Nested')

        cleanup:
        context.close()
    }
}
