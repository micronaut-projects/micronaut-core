package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AddsUnseenRepeatableAnnotationSpec extends AbstractKotlinCompilerSpec {

    void 'test replace simple annotation'() {
        given:
            def definition = buildBeanDefinition('addann.AddUnseenAnnotationsTo', '''
package addann

import io.micronaut.context.annotation.Bean

@Bean
class AddUnseenAnnotationsTo(myField: Any)
''')
        expect:
            definition.getAnnotationValuesByType(MyRequires).size() == 2
            definition.getAnnotationValuesByType(MyRequires).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["foo", "bar"].toSet()
    }

    static class AddUnseenRepeatableTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        @Override

        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "AddUnseenAnnotationsTo") {
                List<AnnotationValue<MyRequires>> values = new ArrayList<>()
                values.add(AnnotationValue.builder(MyRequires).member("property", "foo").build())
                values.add(AnnotationValue.builder(MyRequires).member("property", "bar").build())
                element.annotate(MyRequirements.class, builder -> builder.values(values.toArray(new AnnotationValue[0])))
            }
        }

    }

}
