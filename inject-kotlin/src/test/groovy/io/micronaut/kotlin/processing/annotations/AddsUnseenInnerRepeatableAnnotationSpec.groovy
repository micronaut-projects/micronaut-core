package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AddsUnseenInnerRepeatableAnnotationSpec extends AbstractKotlinCompilerSpec {

    void 'test replace simple annotation'() {
        given:
            def definition = buildBeanDefinition('addann.AddUnseenAnnotationsTo2', '''
package addann

import io.micronaut.context.annotation.Bean

@Bean
class AddUnseenAnnotationsTo2(var myField: Any)
''')
        expect:
            definition.getAnnotationValuesByType(MyRequirements2.MyRequires2).size() == 2
            definition.getAnnotationValuesByType(MyRequirements2.MyRequires2).stream()
                    .flatMap { it.stringValue("property").stream() }
                    .toList().toSet() == ["foo", "bar"].toSet()
    }

    static class AddUnseenRepeatableTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        @Override

        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "AddUnseenAnnotationsTo2") {
                List<AnnotationValue<MyRequirements2.MyRequires2>> values = new ArrayList<>()
                values.add(AnnotationValue.builder(MyRequirements2.MyRequires2).member("property", "foo").build())
                values.add(AnnotationValue.builder(MyRequirements2.MyRequires2).member("property", "bar").build())
                element.annotate(MyRequirements2.class, builder -> builder.values(values.toArray(new AnnotationValue[0])))
            }
        }

    }

}
