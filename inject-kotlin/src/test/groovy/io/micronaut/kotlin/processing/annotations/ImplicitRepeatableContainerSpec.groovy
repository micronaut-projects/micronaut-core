package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.RepeatableAnnotationRemapper
import io.micronaut.inject.visitor.VisitorContext

class ImplicitRepeatableContainerSpec extends AbstractKotlinCompilerSpec {
    void "application-defined implicit containers are discovered and their occurrences are remapped"() {
        when:
        def definition = buildBeanDefinition('implicitreview.Held', '''
package implicitreview
@Retention(AnnotationRetention.RUNTIME)
annotation class ContainerMarker
@Retention(AnnotationRetention.RUNTIME)
annotation class Legacy(val value: String)
@ContainerMarker
@Retention(AnnotationRetention.RUNTIME)
annotation class ManyLegacy(val value: Array<Legacy>)
@jakarta.inject.Singleton
@ManyLegacy([Legacy("a"), Legacy("b")])
class Held
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
@Retention(AnnotationRetention.RUNTIME)
annotation class ContainerMarker
@io.micronaut.context.annotation.Primary
@Retention(AnnotationRetention.RUNTIME)
annotation class Marked(val value: String)
@ContainerMarker
@Retention(AnnotationRetention.RUNTIME)
annotation class ManyMarked(val value: Array<Marked>)
@ManyMarked([Marked("a"), Marked("b")])
@Retention(AnnotationRetention.RUNTIME)
annotation class Composing
@jakarta.inject.Singleton
@Composing
class Composed
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
@Retention(AnnotationRetention.RUNTIME)
annotation class ContainerMarker
@Retention(AnnotationRetention.SOURCE)
annotation class SourceOnly(val value: String)
@ContainerMarker
@Retention(AnnotationRetention.RUNTIME)
annotation class ManySourceOnly(val value: Array<SourceOnly>)
@jakarta.inject.Singleton
@ManySourceOnly([SourceOnly("a")])
class SourceHeld
''')

        then:
        definition.getAnnotationValuesByName('implicitreview.SourceOnly').isEmpty()
        !definition.hasAnnotation('implicitreview.SourceOnly')
    }

    void "an annotation holding the occurrences without being a container is not unwrapped"() {
        when:
        def definition = buildBeanDefinition('implicitreview.Wrapped', '''
package implicitreview
@Retention(AnnotationRetention.RUNTIME)
annotation class Legacy(val value: String)
@Retention(AnnotationRetention.RUNTIME)
annotation class Envelope(val value: Array<Legacy>)
@jakarta.inject.Singleton
@Envelope([Legacy("a")])
class Wrapped
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
