package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Bean;

@Requires(property = "foo")
@Requires(property = "bar")
@Bean
class ReplaceAnnotationsTo {

    @Requires(property = "xyz")
    public Object myField;

}
''')
        expect:
            definition.getAnnotationValuesByType(Requires).size() == 3
            definition.getAnnotationValuesByType(Requires).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["foo", "bar", "xyz"].toSet()
    }

    static class ReplacesRepeatableTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "ReplaceAnnotationsTo") {
                final List<AnnotationValue<Requires>> indexes = Stream.concat(
                        getIndexes(element),
                        element.getFields().stream().flatMap(this::getIndexes)
                ).toList()
                element.annotate(Requirements.class, builder -> builder.values(indexes.toArray(new AnnotationValue[]{})))
            }
        }

        private Stream<AnnotationValue<Requires>> getIndexes(AnnotationMetadata am) {
            if (am.getAnnotation(Requirements).getAnnotations("value", Requires).isEmpty()) {
                throw new IllegalStateException();
            }
            return am.getAnnotationValuesByType(Requires.class).stream();
        }
    }

}
