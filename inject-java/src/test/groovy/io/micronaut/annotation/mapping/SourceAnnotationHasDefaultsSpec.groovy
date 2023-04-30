package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class SourceAnnotationHasDefaultsSpec extends AbstractTypeElementSpec {

    void 'test source annotation has defaults'() {
        given:
            def definition = buildBeanDefinition('addann.SourceDefaultsAnnotationTest', '''
package addann;

import io.micronaut.inject.annotation.ScopeOne;
import io.micronaut.context.annotation.Bean;

@io.micronaut.annotation.mapping.MySourceAnnotation
@Bean
class SourceDefaultsAnnotationTest {
}
''')
        expect:
            definition.hasAnnotation(Seen.class)
    }

    static class TheVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "SourceDefaultsAnnotationTest") {
                def annotation = element.getAnnotation(MySourceAnnotation.class)
                def propertyValue = annotation.getRequiredValue("property", String.class)
                def countValue = annotation.getRequiredValue("count", Integer.class)
                if (propertyValue != "foo" || countValue != 123) {
                    throw new IllegalStateException()
                } else {
                    element.annotate(Seen.class)
                }
            }
        }
    }

}
