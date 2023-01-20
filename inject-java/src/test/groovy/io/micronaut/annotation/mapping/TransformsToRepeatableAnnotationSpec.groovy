package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.TypedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext

class TransformsToRepeatableAnnotationSpec extends AbstractTypeElementSpec {

    void 'test remapping'() {
        given:
            def definition = buildBeanDefinition('addann.TransformToRepeatableAnnotationsTo', '''
package addann;

import io.micronaut.inject.annotation.ScopeOne;
import io.micronaut.context.annotation.Bean;

@Bean
@io.micronaut.annotation.mapping.TransformMeToRepeatable
class TransformToRepeatableAnnotationsTo {

    public Object myField;

}
''')
        expect:
            !definition.hasAnnotation(TransformMeToRepeatable.class)
            definition.getAnnotationValuesByType(MyRequires).size() == 2
            definition.getAnnotationValuesByType(MyRequires).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["fooT", "barT"].toSet()
    }

    static class TheAnnotationTransformer implements TypedAnnotationTransformer<TransformMeToRepeatable> {

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<TransformMeToRepeatable> annotation, VisitorContext visitorContext) {
            return Arrays.asList(
                    AnnotationValue.builder(MyRequires).member("property", "fooT").build(),
                    AnnotationValue.builder(MyRequires).member("property", "barT").build()
            )
        }

        @Override
        Class<TransformMeToRepeatable> annotationType() {
            return TransformMeToRepeatable.class
        }
    }


}
