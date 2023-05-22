package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.stream.Stream

class ReplacesRepeatableAnnotationSpec extends AbstractTypeElementSpec {

    void 'test replace simple annotation'() {
        given:
            def definition = buildBeanDefinition('addann.ReplaceAnnotationsTo', '''
package addann;

import io.micronaut.context.annotation.Bean;

@io.micronaut.annotation.mapping.MyRequires(property = "foo")
@io.micronaut.annotation.mapping.MyRequires(property = "bar")
@Bean
class ReplaceAnnotationsTo {

    @io.micronaut.annotation.mapping.MyRequires(property = "xyz")
    public Object myField;

}
''')
        expect:
            definition.getAnnotationValuesByType(MyRequires).size() == 3
            definition.getAnnotationValuesByType(MyRequires).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["foo", "bar", "xyz"].toSet()
    }

    static class ReplacesRepeatableTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "ReplaceAnnotationsTo") {
                final List<AnnotationValue<MyRequires>> indexes = Stream.concat(
                        getIndexes(element),
                        element.getFields().stream().flatMap(this::getIndexes)
                ).toList()
                element.annotate(MyRequirements.class, builder -> builder.values(indexes.toArray(new AnnotationValue[]{})))
            }
        }

        private Stream<AnnotationValue<MyRequires>> getIndexes(AnnotationMetadata am) {
            if (am.getAnnotation(MyRequirements).getAnnotations("value", MyRequires).isEmpty()) {
                throw new IllegalStateException();
            }
            return am.getAnnotationValuesByType(MyRequires.class).stream();
        }
    }

}
