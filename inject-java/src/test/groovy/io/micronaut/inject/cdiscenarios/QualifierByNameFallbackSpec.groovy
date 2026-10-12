package io.micronaut.inject.cdiscenarios

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.qualifiers.Qualifiers

import java.lang.annotation.Annotation

/**
 * CDI 2.4.2: a bean has the qualifiers it declares. A qualifier built from an annotation given to the API
 * selects the beans that carry it, and not a bean whose name happens to start with the annotation's simple
 * name: {@code @Default} does not select a {@code @Special class DefaultGreeter}. The name convention stays
 * for a qualifier read off an injection point, where {@code @V8 Engine} selects a {@code V8Engine}.
 */
class QualifierByNameFallbackSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package qualifierbyname;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Greeter {
}

@Special
@Prototype
class DefaultGreeter implements Greeter {
}

@Default
@Prototype
class SpecialGreeter implements Greeter {
}

@Prototype
class PlainGreeter implements Greeter {
}

@Default
class HasDefault {
}

@Qualifier
@Retention(RUNTIME)
@interface Default {
}

@Qualifier
@Retention(RUNTIME)
@interface Special {
}
'''

    void 'test byAnnotation(Annotation) selects the beans that carry the annotation only'() {
        given:
        def context = buildContext(SOURCE)
        Class<?> greeter = context.classLoader.loadClass('qualifierbyname.Greeter')
        Annotation defaultAnnotation = context.classLoader.loadClass('qualifierbyname.HasDefault').annotations
            .find { it.annotationType().simpleName == 'Default' }

        expect:
        context.getBeanDefinitions(greeter, Qualifiers.byAnnotation(defaultAnnotation))*.beanType*.simpleName == ['SpecialGreeter']

        cleanup:
        context.close()
    }

    void 'test byAnnotation(AnnotationMetadata, AnnotationValue) selects the beans that carry the annotation only'() {
        given:
        def context = buildContext(SOURCE)
        Class<?> greeter = context.classLoader.loadClass('qualifierbyname.Greeter')
        def defaultValue = AnnotationValue.builder('qualifierbyname.Default').build()

        expect:
        context.getBeanDefinitions(greeter, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, defaultValue))*.beanType*.simpleName == ['SpecialGreeter']

        cleanup:
        context.close()
    }

    void 'test a lookup by the annotation alone and one by name are cached apart, in either order'() {
        given: 'the context caches a lookup by its qualifier, so the two must not be equal'
        def context = buildContext(SOURCE)
        Class<?> greeter = context.classLoader.loadClass('qualifierbyname.Greeter')
        def byAnnotationAlone = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, AnnotationValue.builder('qualifierbyname.Default').build())
        def byName = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, 'qualifierbyname.Default')

        expect:
        byAnnotationAlone != byName
        byName != byAnnotationAlone

        when: 'the lookup by the annotation alone comes first'
        def strictFirst = context.getBeansOfType(greeter, byAnnotationAlone)*.class*.simpleName
        def thenByName = context.getBeansOfType(greeter, byName)*.class*.simpleName

        then:
        strictFirst == ['SpecialGreeter']
        thenByName as Set == ['SpecialGreeter', 'DefaultGreeter'] as Set

        when: 'the lookup by name comes first, in a fresh context'
        context.close()
        context = buildContext(SOURCE)
        greeter = context.classLoader.loadClass('qualifierbyname.Greeter')
        def nameFirst = context.getBeansOfType(greeter, byName)*.class*.simpleName
        def thenStrict = context.getBeansOfType(greeter, byAnnotationAlone)*.class*.simpleName

        then:
        nameFirst as Set == ['SpecialGreeter', 'DefaultGreeter'] as Set
        thenStrict == ['SpecialGreeter']

        and: 'a single bean lookup sees the difference too'
        context.findBean(greeter, byAnnotationAlone).get().class.simpleName == 'SpecialGreeter'

        cleanup:
        context.close()
    }

    void 'test a qualifier read off an injection point still selects a bean by its name'() {
        given:
        def context = buildContext(SOURCE)
        Class<?> greeter = context.classLoader.loadClass('qualifierbyname.Greeter')

        expect: 'the bean carrying the annotation and the bean named after it, as an injection point resolves'
        context.getBeanDefinitions(greeter, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, 'qualifierbyname.Default'))*.beanType*.simpleName as Set == ['SpecialGreeter', 'DefaultGreeter'] as Set

        cleanup:
        context.close()
    }
}
