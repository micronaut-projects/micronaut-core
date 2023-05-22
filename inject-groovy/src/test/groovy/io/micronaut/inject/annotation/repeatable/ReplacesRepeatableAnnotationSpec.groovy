package io.micronaut.inject.annotation.repeatable

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.AllElementsVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.stream.Stream

class ReplacesRepeatableAnnotationSpec extends AbstractBeanDefinitionSpec {
    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, ReplacesRepeatableTypeElementVisitor.name)
    }

    def cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
        AllElementsVisitor.clearVisited()
    }

    void 'test replace simple annotation'() {
        given:
            def definition = buildBeanDefinition('addann.Test', '''
package addann

import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.annotation.ScopeOne;
import io.micronaut.context.annotation.Bean;

@Requires(property = "foo")
@Requires(property = "bar")
@Bean
class Test {

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
            final List<AnnotationValue<Requires>> indexes = Stream.concat(
                    getIndexes(element),
                    element.getFields().stream().flatMap(this::getIndexes)
            ).toList()
            element.annotate(Requirements.class, builder -> builder.values(indexes.toArray(new AnnotationValue[]{})));
        }

        private Stream<AnnotationValue<Requires>> getIndexes(AnnotationMetadata am) {
            return am.getAnnotationValuesByType(Requires.class).stream();
        }
    }

}
