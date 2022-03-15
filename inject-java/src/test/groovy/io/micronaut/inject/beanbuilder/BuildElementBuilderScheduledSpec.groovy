package io.micronaut.inject.beanbuilder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.beans.BeanElementBuilder
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.inject.Singleton

class BuildElementBuilderScheduledSpec extends AbstractTypeElementSpec {

    void "test that bean definitions can be processed on startup"() {
        given:
        def context = buildContext('''
package factorybuilder;

import jakarta.inject.Singleton;

@Singleton
class Foo {
    
}
''')
        expect:
        context.getBeanDefinition(TestBeanScheduled).requiresMethodProcessing()

        cleanup:
        context.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        [new TestAddAssociatedScheduledVisitor()]
    }

    static class TestAddAssociatedScheduledVisitor implements TypeElementVisitor {
        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.hasAnnotation(AnnotationUtil.SINGLETON)){

                context.getClassElement(TestBeanScheduled.class)
                        .ifPresent((scheduled) -> {
                            final BeanElementBuilder beanElementBuilder = element.addAssociatedBean(scheduled);
                            final ElementQuery<MethodElement> query = ElementQuery.ALL_METHODS
                                    .onlyInstance()
                                    .onlyDeclared()
                            beanElementBuilder.withMethods(query, { method ->
                                method.executable(true)
                            })
                        });
            }
        }
    }

    static class TestBeanScheduled {
        void scheduleMe() {
            println "running"
        }
    }
}
