package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationView
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class AnnotationViewSpec extends AbstractTypeElementSpec {

    static final List<String> MEMBERS = ['value', 'name', 'count', 'flag', 'type', 'kind', 'names', 'empty', 'missing', '']

    @Language("java")
    static final String SOURCE = '''\
package test;

import io.micronaut.context.annotation.*;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@interface Ann {
    String value() default "def";
    String name() default "defName";
    int count() default 7;
    boolean flag() default true;
    Class<?> type() default Object.class;
    RetentionPolicy kind() default RetentionPolicy.CLASS;
    String[] names() default {"a"};
    String[] empty() default {};
}

@Retention(RetentionPolicy.RUNTIME)
@Ann(value = "fromStereotype", count = 3)
@interface Stereo {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Numbers {
    String count();
    String flag();
    String kind();
}

@jakarta.inject.Singleton
@Ann(value = "declared", count = 42, flag = false, type = String.class, kind = RetentionPolicy.SOURCE, names = {"x", "y"}, empty = {})
@Numbers(count = "12", flag = "true", kind = "RUNTIME")
@Property(name = "first", value = "1")
@Property(name = "second", value = "2")
@Requires(property = "x")
class Test {

    @Ann
    @Executable
    void defaults() {}

    @Stereo
    @Executable
    void stereotype() {}

    @Ann(name = "method")
    @Executable
    void merged() {}

    @Executable
    void none() {}
}
'''

    @Language("java")
    static final String DEFAULTS_SOURCE = '''\
package test;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@interface Ann {
    String value() default "def";
    String name() default "defName";
    int count() default 7;
}

@Retention(RetentionPolicy.RUNTIME)
@Ann(value = "fromStereotype", count = 3)
@interface Stereo {
}

@jakarta.inject.Singleton
@Ann
class Defaults {
}

@jakarta.inject.Singleton
@Stereo
class Stereotyped {
}
'''

    void "the view of compile-time metadata reads the same members as findAnnotation"() {
        given:
        AnnotationMetadata type = buildTypeAnnotationMetadata(SOURCE)

        expect:
        assertEquivalent(type, 'test.Ann')
        assertEquivalent(type, 'test.Numbers')
        assertEquivalent(type, Property.name)
        assertEquivalent(type, Requires.name)
        assertEquivalent(type, Singleton.name)
        type.getAnnotationView('test.Missing') == null
        type.getAnnotationView('') == null
    }

    void "the view of runtime metadata reads the same members as findAnnotation"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Test', SOURCE)

        expect:
        assertProviderEquivalent(definition, 'test.Ann')
        assertEquivalent(definition.annotationMetadata, 'test.Ann')
        assertProviderEquivalent(definition, 'test.Numbers')
        assertProviderEquivalent(definition, Property.name)
        assertProviderEquivalent(definition, Requires.name)
        assertProviderEquivalent(definition, Singleton.name)
        definition.getAnnotationView('test.Missing') == null

        and: 'the repeated annotation is answered by the first occurrence'
        definition.getAnnotationView(Property).stringValue('name').get() == 'first'
        definition.getAnnotationView(Property.name).stringValue('name').get() == 'first'
        definition.getAnnotationView(Requires).stringValue('property').get() == 'x'

        and: 'the defaults do not apply, as with the typed accessors of the annotation value'
        def defaults = buildBeanDefinition('test.Defaults', DEFAULTS_SOURCE)
        assertProviderEquivalent(defaults, 'test.Ann')
        defaults.getAnnotationView('test.Ann').stringValue('name').isEmpty()
        defaults.getAnnotationView('test.Ann').intValue('count').isEmpty()
        defaults.getAnnotationView('test.Ann').getClass().simpleName == 'MapAnnotationView'

        and: 'an annotation present as a stereotype is read from the stereotypes'
        def stereotyped = buildBeanDefinition('test.Stereotyped', DEFAULTS_SOURCE)
        !stereotyped.hasDeclaredAnnotation('test.Ann')
        assertProviderEquivalent(stereotyped, 'test.Ann')
        stereotyped.getAnnotationView('test.Ann').stringValue().get() == 'fromStereotype'
        stereotyped.getAnnotationView('test.Ann').intValue('count').asInt == 3
        stereotyped.getAnnotationView('test.Ann').getClass().simpleName == 'MapAnnotationView'
    }

    void "the view of a hierarchy reads the same members as findAnnotation"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Test', SOURCE)

        expect:
        ['defaults', 'stereotype', 'merged', 'none'].every { method ->
            AnnotationMetadata metadata = definition.findMethod(method).get().annotationMetadata
            AnnotationMetadata hierarchy = new AnnotationMetadataHierarchy(definition.annotationMetadata, metadata)
            ['test.Ann', 'test.Numbers', Property.name, Requires.name, Singleton.name, 'test.Missing'].every { annotation ->
                assertEquivalent(metadata, annotation) && assertEquivalent(hierarchy, annotation)
            }
        }

        and: 'an annotation present in both layers is merged'
        def merged = new AnnotationMetadataHierarchy(definition.annotationMetadata, definition.findMethod('merged').get().annotationMetadata)
        merged.getAnnotationView('test.Ann').stringValue('name').get() == 'method'
        merged.getAnnotationView('test.Ann').stringValue().get() == 'declared'

        and: 'an annotation present in one layer is not copied'
        def single = definition.findMethod('none').get().annotationMetadata
        single instanceof AnnotationMetadataHierarchy
        single.getAnnotationView('test.Ann').getClass().simpleName == 'MapAnnotationView'
        single.getAnnotationView(Singleton).getClass().simpleName == 'MapAnnotationView'
    }

    void "the default implementation falls back to findAnnotation"() {
        given:
        AnnotationMetadata metadata = buildBeanDefinition('test.Test', SOURCE).annotationMetadata
        AnnotationMetadata delegating = new AnnotationMetadata() {
            @Override
            <T extends java.lang.annotation.Annotation> Optional<AnnotationValue<T>> findAnnotation(String annotation) {
                return metadata.findAnnotation(annotation)
            }

            @Override
            <T extends java.lang.annotation.Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(String annotation) {
                return metadata.findDeclaredAnnotation(annotation)
            }
        }

        expect:
        delegating.getAnnotationView('test.Ann').stringValue().get() == 'declared'
        delegating.getAnnotationView('test.Missing') == null
        AnnotationMetadata.EMPTY_METADATA.getAnnotationView('test.Ann') == null
        AnnotationMetadata.EMPTY_METADATA.getAnnotationView(Singleton) == null
    }

    private static boolean assertEquivalent(AnnotationMetadata metadata, String annotation) {
        AnnotationValue<?> value = metadata.findAnnotation(annotation).orElse(null)
        AnnotationView view = metadata.getAnnotationView(annotation)
        if (value == null) {
            assert view == null
            return true
        }
        assert view != null
        assert view.annotationName() == value.annotationName
        for (String member : MEMBERS) {
            assert view.isPresent(member) == value.isPresent(member)
            assert view.stringValue(member) == value.stringValue(member)
            assert view.intValue(member) == value.intValue(member)
            assert view.booleanValue(member) == value.booleanValue(member)
            assert view.isTrue(member) == value.isTrue(member)
            assert view.classValue(member) == value.classValue(member)
            assert view.enumValue(member, java.lang.annotation.RetentionPolicy) == value.enumValue(member, java.lang.annotation.RetentionPolicy)
        }
        assert view.stringValue() == value.stringValue()
        return true
    }

    private static boolean assertProviderEquivalent(AnnotationMetadataProvider provider, String annotation) {
        return assertEquivalent(provider.annotationMetadata, annotation) && assertEquivalent((AnnotationMetadata) provider, annotation)
    }
}
