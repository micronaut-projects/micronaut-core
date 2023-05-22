package io.micronaut.inject.annotation

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.AllElementsVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class RemoveAnnotationSpec extends AbstractBeanDefinitionSpec {
    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, ReplacingTypeElementVisitor.name)
    }

    def cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
        AllElementsVisitor.clearVisited()
    }

    void 'test replace simple annotation'() {
        given:
        def definition = buildBeanDefinition('removeann.Test', '''
package removeann;

import io.micronaut.inject.annotation.ScopeOne;
import io.micronaut.context.annotation.Bean;

@ScopeOne
@Bean
class Test {

}
''')
        expect:
        definition
        definition.hasStereotype(AnnotationUtil.SCOPE)
        definition.hasDeclaredAnnotation(Prototype)
        !definition.hasDeclaredAnnotation(ScopeOne)
        def stereotypes = definition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE)
        stereotypes.contains(Prototype.name)
        stereotypes.size() == 1
    }

    static class ReplacingTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (System.getProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY) ==  ReplacingTypeElementVisitor.name) {
                element.removeAnnotation(ScopeOne)
                element.annotate(Prototype)
            }
        }
    }

}
