package io.micronaut.inject.qualifiers.annotationmember

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * Every occurrence of a qualifier records which of its members are {@code @NonBinding}, written or not, so
 * that a lookup writing a member the candidate left at its default can tell that the member does not bind.
 */
class NonBindingMembersRecordedSpec extends AbstractTypeElementSpec {

    void 'test an occurrence leaving a @NonBinding member at its default records it'() {
        given:
        def definition = buildBeanDefinition('nonbindingdefault.Test', '''
package nonbindingdefault;

import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Cylinders(8)
@Singleton
class Test {
}

@Qualifier
@Retention(RUNTIME)
@interface Cylinders {
    int value();

    @NonBinding
    String description() default "";
}
''')
        def metadata = definition.getAnnotationMetadata()

        expect: 'the annotation lists the member, and so does the qualifier stereotype the resolution reads'
        nonBinding(metadata.getAnnotation('nonbindingdefault.Cylinders')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(metadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        AnnotationUtil.resolveNonBindingMembers(metadata) as Set == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set

        and: 'an occurrence writing no member at all records it too'
        nonBinding(definition.getAnnotationMetadata().getAnnotation('nonbindingdefault.Cylinders')).contains('description')
    }

    void 'test an annotation a visitor adds records its @NonBinding members'() {
        given:
        def element = buildClassElement('''
package nonbindingadded;

import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Qualifier;

class Test {
}

@Qualifier
@interface Cylinders {
    int value();

    @NonBinding
    String description() default "";
}
''')

        when:
        element.annotate('nonbindingadded.Cylinders', { it.value(6) })

        then:
        nonBinding(element.getAnnotation('nonbindingadded.Cylinders')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(element.getAnnotation(AnnotationUtil.QUALIFIER)) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
    }

    void 'test two annotation interfaces whose members are annotated with each other'() {
        given: 'reading the members of one reads an occurrence of the other, whose members name the first again'
        def definition = buildBeanDefinition('nonbindingmutual.Test', '''
package nonbindingmutual;

import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Left(1)
@Right(2)
@Singleton
class Test {
}

@Qualifier
@Retention(RUNTIME)
@interface Left {
    @Right(0)
    int value();

    @NonBinding
    @Right(0)
    String note() default "";
}

@Qualifier
@Retention(RUNTIME)
@interface Right {
    @Left(0)
    int value();

    @NonBinding
    @Left(0)
    String note() default "";
}
''')
        def metadata = definition.getAnnotationMetadata()

        expect:
        nonBinding(metadata.getAnnotation('nonbindingmutual.Left')) == ['note', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(metadata.getAnnotation('nonbindingmutual.Right')) == ['note', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
    }

    void 'test a lookup writing a @NonBinding member finds a bean that left it at its default'() {
        given:
        def context = buildContext('''
package nonbindinglookup;

import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Engine {
}

@Cylinders(8)
@Singleton
class V8Engine implements Engine {
}

@Cylinders(6)
@Singleton
class V6Engine implements Engine {
}

@Qualifier
@Retention(RUNTIME)
@interface Cylinders {
    int value();

    @NonBinding
    String description() default "";
}
''')
        Class<?> engine = context.classLoader.loadClass('nonbindinglookup.Engine')
        def written = AnnotationValue.builder('nonbindinglookup.Cylinders').value(8).member('description', 'eight').build()

        expect: 'the qualifier is built with no metadata saying what binds; the candidate says it'
        context.getBeanDefinitions(engine, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, written))*.beanType*.simpleName == ['V8Engine']

        cleanup:
        context.close()
    }

    private static Set<String> nonBinding(AnnotationValue<?> annotation) {
        annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set
    }
}
