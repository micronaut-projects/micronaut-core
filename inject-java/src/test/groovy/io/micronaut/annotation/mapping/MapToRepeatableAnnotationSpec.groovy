package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.TypedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext

class MapToRepeatableAnnotationSpec extends AbstractTypeElementSpec {

    void 'test remapping'() {
        given:
            def definition = buildBeanDefinition('addann.RemapToRepeatableAnnotationsTo', '''
package addann;

import io.micronaut.context.annotation.Bean;

@Bean
@io.micronaut.annotation.mapping.MapMeToRepeatable
class RemapToRepeatableAnnotationsTo {

    public Object myField;

}
''')
        expect:
            definition.hasAnnotation(MapMeToRepeatable)
            definition.getAnnotationValuesByType(MyRequires).size() == 2
            definition.getAnnotationValuesByType(MyRequires).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["fooM", "barM"].toSet()
    }

    static class TheAnnotationMapper implements TypedAnnotationMapper<MapMeToRepeatable> {

        @Override
        List<AnnotationValue<?>> map(AnnotationValue<MapMeToRepeatable> annotation, VisitorContext visitorContext) {
            return Arrays.asList(
                    AnnotationValue.builder(MyRequires).member("property", "fooM").build(),
                    AnnotationValue.builder(MyRequires).member("property", "barM").build()
            )
        }

        @Override
        Class<MapMeToRepeatable> annotationType() {
            return MapMeToRepeatable.class
        }
    }


}
