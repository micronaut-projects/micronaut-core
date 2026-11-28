package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection

/**
 * A visitor that annotates an annotation type, as {@code @ClassImport} does with {@code @ImportedClass}, adds that
 * annotation to where the annotation type is used, and nothing else: the stereotypes the annotation type declares
 * stay as the aliases of the use set them.
 */
class ImportedAnnotationTypeAliasSpec extends AbstractTypeElementSpec {

    void "an indexed alias applies to a use of an annotation type that is not imported"() {
        when:
        def introspection = buildBeanIntrospection('test.Bean', SOURCE)

        then:
        tags(introspection) == ['a', 'x']
    }

    void "an indexed alias applies to a use of an annotation type that is imported"() {
        when:
        def introspection = buildBeanIntrospection('test.Bean', SOURCE + '''
@io.micronaut.context.annotation.ClassImport(classes = Composite.class)
class Imports {}
''')

        then:
        tags(introspection) == ['a', 'x']
    }

    void "a non-indexed alias applies to a use of an annotation type that is imported, which adds no other occurrence"() {
        when:
        def introspection = buildBeanIntrospection('test.Bean', SOURCE.replace('index = 1', 'index = -1') + '''
@io.micronaut.context.annotation.ClassImport(classes = Composite.class)
class Imports {}
''')

        then:
        tags(introspection) == ['x']
    }

    private static List<String> tags(BeanIntrospection<?> introspection) {
        introspection.getRequiredProperty('value', String).annotationMetadata
            .getAnnotationValuesByName('test.Tag')
            .collect { it.stringValue().get() }
    }

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.*;

@Repeatable(Tags.class)
@Retention(RetentionPolicy.RUNTIME)
@interface Tag { String value(); }

@Retention(RetentionPolicy.RUNTIME)
@interface Tags { Tag[] value(); }

@Tag("a")
@Tag("b")
@Retention(RetentionPolicy.RUNTIME)
@interface Composite {
    @AliasFor(annotation = Tag.class, member = "value", index = 1)
    String second() default "x";
}

@Introspected
class Bean {
    @Composite(second = "x")
    private String value;
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
'''
}
