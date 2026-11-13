package io.micronaut.annotation.implicitrepeat

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.AnnotationRemapper
import io.micronaut.inject.visitor.VisitorContext

class ImplicitRepeatableContainerSpec extends AbstractTypeElementSpec {

    void "the occurrences in a container a remapper declares are those of a repeatable annotation"() {
        when:
        def definition = buildBeanDefinition('test.Held', '''
package test;

import io.micronaut.annotation.implicitrepeat.Legacy;

@jakarta.inject.Singleton
@Legacy.List({@Legacy("a"), @Legacy("b")})
class Held {
}
''')

        then:
        definition.getAnnotationValuesByType(Legacy)*.stringValue()*.get() == ['a', 'b']
        definition.getAnnotationValuesByName(Legacy.name).size() == 2
        definition.findRepeatableAnnotation(Legacy).get() == Legacy.List.name
        definition.synthesizeAnnotationsByType(Legacy)*.value() == ['a', 'b']
    }

    void "the occurrences a remapper produces are stored in the container it declares"() {
        when:
        def definition = buildBeanDefinition('test.Made', '''
package test;

import io.micronaut.annotation.implicitrepeat.MakeTwoLegacy;

@jakarta.inject.Singleton
@MakeTwoLegacy
class Made {
}
''')

        then:
        !definition.hasAnnotation(MakeTwoLegacy)
        definition.getAnnotationValuesByType(Legacy)*.stringValue()*.get() as Set == ['x', 'y'] as Set
        definition.hasAnnotation(Legacy.List)
    }

    static class LegacyRemapper implements AnnotationRemapper {

        @Override
        String getPackageName() {
            return "io.micronaut.annotation.implicitrepeat"
        }

        @Override
        List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
            if (annotation.annotationName == MakeTwoLegacy.name) {
                return [
                    AnnotationValue.builder(Legacy).value('x').build(),
                    AnnotationValue.builder(Legacy).value('y').build()
                ] as List<AnnotationValue<?>>
            }
            return [annotation] as List<AnnotationValue<?>>
        }

        @Override
        Map<String, String> getRepeatableContainers() {
            return [(Legacy.name): Legacy.List.name]
        }
    }
}
