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
