package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.RepeatableAnnotationRemapper
import io.micronaut.inject.visitor.VisitorContext

class ImplicitRepeatableContainerSpec extends AbstractBeanDefinitionSpec {
    void "application-defined implicit containers are discovered and their occurrences are remapped"() {
        when:
        def definition = buildBeanDefinition('implicitreview.Held', '''
package implicitreview
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
@Retention(RetentionPolicy.RUNTIME)
@interface ContainerMarker { }
@Retention(RetentionPolicy.RUNTIME)
@interface Legacy { String value() }
@ContainerMarker
@Retention(RetentionPolicy.RUNTIME)
@interface ManyLegacy { Legacy[] value() }
@jakarta.inject.Singleton
@ManyLegacy([@Legacy('a'), @Legacy('b')])
class Held { }
''')
        def repeatedType = (Class) definition.class.classLoader.loadClass('implicitreview.Legacy')

        then:
        definition.getAnnotationValuesByType(repeatedType)*.stringValue()*.get() == ['a', 'b']
        definition.getAnnotationValuesByName('implicitreview.Legacy')*.booleanValue('seen')*.orElse(false) == [true, true]
        definition.findRepeatableAnnotation('implicitreview.Legacy').get() == 'implicitreview.ManyLegacy'
    }

    void "an occurrence held in an implicit container contributes its stereotypes, also through a composed annotation"() {
        when:
        def definition = buildBeanDefinition('implicitreview.Composed', '''
package implicitreview
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
@Retention(RetentionPolicy.RUNTIME)
@interface ContainerMarker { }
@io.micronaut.context.annotation.Primary
@Retention(RetentionPolicy.RUNTIME)
@interface Marked { String value() }
@ContainerMarker
@Retention(RetentionPolicy.RUNTIME)
@interface ManyMarked { Marked[] value() }
@ManyMarked([@Marked('a'), @Marked('b')])
@Retention(RetentionPolicy.RUNTIME)
@interface Composing { }
@jakarta.inject.Singleton
@Composing
class Composed { }
''')

        then:
        definition.hasStereotype(io.micronaut.context.annotation.Primary)
        definition.getAnnotationValuesByName('implicitreview.Marked')*.stringValue()*.get() == ['a', 'b']
        definition.findRepeatableAnnotation('implicitreview.Marked').get() == 'implicitreview.ManyMarked'
    }

    void "a source-only occurrence held in an implicit container stays out of the metadata"() {
        when:
        def definition = buildBeanDefinition('implicitreview.SourceHeld', '''
package implicitreview
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
@Retention(RetentionPolicy.RUNTIME)
@interface ContainerMarker { }
@Retention(RetentionPolicy.SOURCE)
@interface SourceOnly { String value() }
@ContainerMarker
@Retention(RetentionPolicy.RUNTIME)
@interface ManySourceOnly { SourceOnly[] value() }
@jakarta.inject.Singleton
@ManySourceOnly([@SourceOnly('a')])
class SourceHeld { }
''')

        then:
        definition.getAnnotationValuesByName('implicitreview.SourceOnly').isEmpty()
        !definition.hasAnnotation('implicitreview.SourceOnly')
    }

    void "an annotation holding the occurrences without being a container is not unwrapped"() {
        when:
        def definition = buildBeanDefinition('implicitreview.Wrapped', '''
package implicitreview
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
@Retention(RetentionPolicy.RUNTIME)
@interface Legacy { String value() }
@Retention(RetentionPolicy.RUNTIME)
@interface Envelope { Legacy[] value() }
@jakarta.inject.Singleton
@Envelope([@Legacy('a')])
class Wrapped { }
''')

        then:
        definition.getAnnotationValuesByName('implicitreview.Legacy').isEmpty()
        definition.getAnnotationValuesByName('implicitreview.Envelope').size() == 1
    }

    static class Remapper implements RepeatableAnnotationRemapper {
        @Override
        String getPackageName() { 'implicitreview' }

        @Override
        boolean isRepeatableAnnotationContainer(AnnotationValue<?> annotation, VisitorContext context) {
            context.getClassElement(annotation.annotationName)
                .map { it.hasAnnotation('implicitreview.ContainerMarker') }.orElse(false)
        }

        @Override
        List<AnnotationValue<?>> remap(AnnotationValue<?> value, VisitorContext context) {
            value.annotationName == 'implicitreview.Legacy' && !value.contains('seen')
                ? [value.mutate().member('seen', true).build()] : [value]
        }
    }
}
