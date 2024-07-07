package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.annotation.AnnotationRemapper
import io.micronaut.inject.visitor.VisitorContext

class RemapToRepeatableAnnotationSpec extends AbstractTypeElementSpec {

    void 'test remapping'() {
        given:
            def definition = buildBeanDefinition('addann.RemapToRepeatableAnnotationsTo', '''
package addann;

import io.micronaut.context.annotation.Bean;

@Bean
@io.micronaut.annotation.mapping.RemapMeToRepeatable
class RemapToRepeatableAnnotationsTo {

    public Object myField;

}
''')
        expect:
            !definition.hasAnnotation(RemapMeToRepeatable)
            definition.getAnnotationValuesByType(MyRequires).size() == 2
            definition.getAnnotationValuesByType(MyRequires).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["fooR", "barR"].toSet()
    }

    static class TheAnnotationMapper implements AnnotationRemapper {

        @NonNull
        @Override
        String getPackageName() {
            return "io.micronaut.annotation.mapping"
        }

        @NonNull
        @Override
        List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
            if (annotation.getAnnotationName() == RemapMeToRepeatable.class.name) {
                return Arrays.asList(
                        AnnotationValue.builder(MyRequires).member("property", "fooR").build(),
                        AnnotationValue.builder(MyRequires).member("property", "barR").build()
                )
            }
            return Collections.singletonList(annotation)
        }
    }


}
