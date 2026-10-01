package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * A parameter of an inherited method and an inherited field are the same members whether they are
 * read through the declaring class or through a subclass, so an annotation mutation made by a
 * visitor through the declaring class must be visible to a visitor reading them through the subclass.
 */
class InheritedMemberAnnotationMutationSpec extends AbstractTypeElementSpec {

    void 'mutations of an inherited parameter and field made through the declaring class are visible through the subclass'() {
        given:
        BaseVisitor.visited = false
        SubVisitor.reset()

        when:
        def definition = buildBeanDefinition('test.Sub', '''
package test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import io.micronaut.context.annotation.Executable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Retention(RetentionPolicy.RUNTIME)
@interface Marker {}

@Retention(RetentionPolicy.RUNTIME)
@interface Added {}

abstract class Base {

    @Inject
    @Marker
    protected String f;

    @Executable
    public void inherited(@Marker String a) {
    }
}

@Singleton
class Sub extends Base {
}
''')

        then: 'the visitor on Base ran before the visitor on Sub and both members were read through Sub'
        BaseVisitor.visited
        SubVisitor.baseVisitedFirst
        SubVisitor.parameterOwner == 'test.Sub'
        SubVisitor.fieldOwner == 'test.Sub'

        and: 'the parameter read through Sub reflects the mutation made through Base'
        SubVisitor.parameterAnnotations.contains('test.Added')
        !SubVisitor.parameterAnnotations.contains('test.Marker')

        and: 'the field read through Sub reflects the mutation made through Base'
        SubVisitor.fieldAnnotations.contains('test.Added')
        !SubVisitor.fieldAnnotations.contains('test.Marker')

        and: 'the compiled definition of Sub, whose members are read through Sub, carries the mutation as well'
        def argument = definition.findPossibleMethods('inherited').findAny().get().arguments[0]
        argument.annotationMetadata.hasAnnotation('test.Added')
        !argument.annotationMetadata.hasAnnotation('test.Marker')
        def field = definition.injectedFields[0]
        field.name == 'f'
        field.asArgument().annotationMetadata.hasAnnotation('test.Added')
        !field.asArgument().annotationMetadata.hasAnnotation('test.Marker')
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new BaseVisitor(), new SubVisitor()]
    }

    /**
     * Visits Base and swaps @Marker for @Added on the parameter of {@code inherited} and on the field {@code f}.
     */
    static class BaseVisitor implements TypeElementVisitor<Object, Object> {

        static boolean visited

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name != 'test.Base') {
                return
            }
            def parameter = element.getEnclosedElement(ElementQuery.ALL_METHODS.named('inherited')).get().parameters[0]
            parameter.removeAnnotation('test.Marker')
            parameter.annotate('test.Added')

            def field = element.getEnclosedElement(ElementQuery.ALL_FIELDS.named('f')).get()
            field.removeAnnotation('test.Marker')
            field.annotate('test.Added')
            visited = true
        }
    }

    /**
     * Visits Sub and records what the inherited parameter and field look like when read through Sub.
     */
    static class SubVisitor implements TypeElementVisitor<Object, Object> {

        static boolean baseVisitedFirst
        static String parameterOwner
        static String fieldOwner
        static List<String> parameterAnnotations
        static List<String> fieldAnnotations

        static void reset() {
            baseVisitedFirst = false
            parameterOwner = null
            fieldOwner = null
            parameterAnnotations = null
            fieldAnnotations = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name != 'test.Sub') {
                return
            }
            baseVisitedFirst = BaseVisitor.visited

            def method = element.getEnclosedElement(ElementQuery.ALL_METHODS.named('inherited')).get()
            parameterOwner = method.owningType.name
            parameterAnnotations = method.parameters[0].annotationMetadata.annotationNames.asList()

            def field = element.getEnclosedElement(ElementQuery.ALL_FIELDS.named('f')).get()
            fieldOwner = field.owningType.name
            fieldAnnotations = field.annotationMetadata.annotationNames.asList()
        }
    }
}
