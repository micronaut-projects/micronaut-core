package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.stream.Stream

class AddsRepeatableAnnotationSpec extends AbstractKotlinCompilerSpec {

    void 'test replace simple annotation'() {
        given:
            def definition = buildBeanDefinition('addann.AddAnnotationsTo', '''
package addann

import io.micronaut.context.annotation.Bean

@io.micronaut.kotlin.processing.annotations.MyRequires(property = "foo")
@io.micronaut.kotlin.processing.annotations.MyRequires(property = "bar")
@Bean
class AddAnnotationsTo(
@io.micronaut.kotlin.processing.annotations.MyRequires(property = "xyz")
val myField: Any)
''')
        expect:
            definition.getAnnotationValuesByType(MyRequires).size() == 3
            definition.getAnnotationValuesByType(MyRequires).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["foo", "bar", "xyz"].toSet()
    }

    static class AddRepeatableTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "AddAnnotationsTo") {
                element.annotate(MyRequirements.class, builder -> builder.values(
                        element.getFields().stream().flatMap(this::getIndexes).toArray(AnnotationValue[]::new)))
            }
        }

        private Stream<AnnotationValue<MyRequires>> getIndexes(AnnotationMetadata am) {
            if (am.getAnnotation(MyRequirements).getAnnotations("value", MyRequires).isEmpty()) {
                throw new IllegalStateException();
            }
            return am.getAnnotationValuesByType(MyRequires.class).stream()
        }
    }

}
