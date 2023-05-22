package io.micronaut.inject.beanbuilder

import groovy.transform.PackageScope
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.beans.BeanElementBuilder
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class BuildElementBuilderProcessedMethodsSpec extends AbstractTypeElementSpec {

    void "test that bean definitions can be processed on startup"() {
        given:
        def context = buildContext('''
package factorybuilder;

import jakarta.inject.Singleton;

@Singleton
class Foo {

}
''')

        when:
        def definition = context.getBeanDefinition(TestBeanScheduled)
        def method = definition.getRequiredMethod("scheduleMe")
        def methodWithArgs = definition.getRequiredMethod("scheduleAnother", String, String)

        then:
        definition.requiresMethodProcessing()
        method != null
        methodWithArgs != null

        when:
        def testBean = context.getBean(TestBeanScheduled)

        then:
        method.invoke(testBean) == 'good'
        methodWithArgs.invoke(testBean, "1", "2") == "good 1 2"

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
        @PackageScope
        String scheduleMe() {
            "good"
        }

        @PackageScope
        String scheduleOne(String one) {
            "good $one"
        }

        @PackageScope
        String scheduleAnother(String one, String two) {
            "good $one $two"
        }
    }
}
