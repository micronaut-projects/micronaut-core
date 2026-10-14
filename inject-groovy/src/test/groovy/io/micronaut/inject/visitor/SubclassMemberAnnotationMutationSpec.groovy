package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery

/**
 * An annotation mutation a visitor makes on an inherited field or on a parameter of an inherited method while
 * visiting one subclass belongs to that subclass: another subclass of the same base, and the base itself, keep
 * the source annotations.
 */
class SubclassMemberAnnotationMutationSpec extends AbstractBeanDefinitionSpec {

    void 'mutations of inherited members made through one subclass are not visible through another subclass'() {
        given:
        MarkingVisitor.reset()

        when:
        def context = buildContext('''
package test

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Retention(RetentionPolicy.RUNTIME)
@interface Marked {}

@Singleton
class Foo {}

@Singleton
class Bar {}

abstract class Base {

    @Inject
    protected Foo foo

    protected List<Bar> bars

    @Inject
    void setBars(List<Bar> bars) {
        this.bars = bars
    }
}

@Singleton
class B extends Base {
}

@Singleton
class C extends Base {
}
''')
        BeanDefinition<?> b = context.getBeanDefinition(context.classLoader.loadClass('test.B'))
        BeanDefinition<?> c = context.getBeanDefinition(context.classLoader.loadClass('test.C'))

        then: 'the members were annotated through B'
        MarkingVisitor.fieldOwner == 'test.B'
        MarkingVisitor.parameterOwner == 'test.B'

        and: 'the definition of B carries the mutation'
        b.injectedFields.size() == 1
        b.injectedFields[0].annotationMetadata.hasAnnotation('test.Marked')
        b.injectedMethods.size() == 1
        b.injectedMethods[0].arguments[0].annotationMetadata.hasAnnotation('test.Marked')

        and: 'the definition of C keeps the source annotations'
        c.injectedFields.size() == 1
        !c.injectedFields[0].annotationMetadata.hasAnnotation('test.Marked')
        c.injectedMethods.size() == 1
        !c.injectedMethods[0].arguments[0].annotationMetadata.hasAnnotation('test.Marked')

        and: 'the members read through Base keep the source annotations'
        !MarkingVisitor.baseFieldAnnotations.contains('test.Marked')
        !MarkingVisitor.baseParameterAnnotations.contains('test.Marked')

        cleanup:
        context.close()
    }

    /**
     * Annotates the inherited injection points of B with @Marked while visiting B, and records once every type was
     * visited what the members look like read through Base.
     */
    static class MarkingVisitor implements TypeElementVisitor<Object, Object> {

        static String fieldOwner
        static String parameterOwner
        static List<String> baseFieldAnnotations
        static List<String> baseParameterAnnotations
        static ClassElement base

        static void reset() {
            fieldOwner = null
            parameterOwner = null
            baseFieldAnnotations = null
            baseParameterAnnotations = null
            base = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'test.Base') {
                base = element
            }
            if (element.name != 'test.B') {
                return
            }
            def field = element.getEnclosedElement(ElementQuery.ALL_FIELDS.named('foo')).get()
            fieldOwner = field.owningType.name
            field.annotate('test.Marked')

            def method = element.getEnclosedElement(ElementQuery.ALL_METHODS.named('setBars')).get()
            parameterOwner = method.owningType.name
            method.parameters[0].annotate('test.Marked')
        }

        @Override
        void finish(VisitorContext visitorContext) {
            if (base == null) {
                return
            }
            baseFieldAnnotations = base.getEnclosedElement(ElementQuery.ALL_FIELDS.named('foo')).get().annotationMetadata.annotationNames.asList()
            baseParameterAnnotations = base.getEnclosedElement(ElementQuery.ALL_METHODS.named('setBars')).get().parameters[0].annotationMetadata.annotationNames.asList()
        }
    }
}
