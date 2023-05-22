package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.TypedAnnotationMapper
import io.micronaut.inject.visitor.VisitorContext

class MappedValueHasDefaultSpec extends AbstractTypeElementSpec {

    void 'test remapping'() {
        given:
            def definition = buildBeanDefinition('addann.MappedValueHasDefault', '''
package addann;

import io.micronaut.context.annotation.Bean;

@Bean
@io.micronaut.annotation.mapping.MySourceAnnotation2
class MappedValueHasDefault {

    public Object myField;

}
''')
        expect:
            !definition.hasAnnotation(MySourceAnnotation2)
            definition.getAnnotationValuesByType(MyRequires).size() == 2
            definition.getAnnotationValuesByType(MyRequires).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["fooX", "barX"].toSet()
    }

    static class TheAnnotationMapper implements TypedAnnotationMapper<MySourceAnnotation2> {

        @Override
        List<AnnotationValue<?>> map(AnnotationValue<MySourceAnnotation2> annotation, VisitorContext visitorContext) {
            def propertyValue = annotation.getRequiredValue("property", String.class)
            def countValue = annotation.getRequiredValue("count", Integer.class)
            if (propertyValue != "foo" || countValue != 123) {
                throw new IllegalStateException()
            }
            return Arrays.asList(
                    AnnotationValue.builder(MyRequires).member("property", "fooX").build(),
                    AnnotationValue.builder(MyRequires).member("property", "barX").build()
            )
        }

        @Override
        Class<MySourceAnnotation2> annotationType() {
            return MySourceAnnotation2.class
        }
    }


}
