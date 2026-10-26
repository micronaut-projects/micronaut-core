package io.micronaut.inject.annotation.modify

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import org.jspecify.annotations.NonNull

/**
 * Annotating the members of a Groovy property: the setter parameter and the accessors Micronaut synthesizes for it
 * are read through the property, so they are annotated through it and the annotations reach the written metadata.
 */
class AnnotatePropertyMembersSpec extends AbstractBeanDefinitionSpec {

    private static final List<String> MEMBER_ANNOTATIONS = ['test.Property', 'test.Field', 'test.Getter', 'test.Setter', 'test.SetterParameter']
    private static final List<String> TYPE_ARGUMENT_ANNOTATIONS = ['test.GetterTypeArgument', 'test.SetterTypeArgument']

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, AnnotatePropertyMembersVisitor.name)
    }

    def cleanup() {
        System.clearProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY)
    }

    void 'test annotating the members of a groovy property is kept in the introspection'() {
        when:
        def introspection = buildBeanIntrospection('annotatemembers.Test', '''
package annotatemembers

import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.annotation.modify.MyAnnotation

@Introspected
class Test {
    @MyAnnotation
    List<String> webs
    List<String> other
}
''')
        def webs = introspection.getRequiredProperty('webs', List)
        def other = introspection.getRequiredProperty('other', List)

        then:
        webs.annotationMetadata.annotationNames.containsAll(MEMBER_ANNOTATIONS)
        webs.asArgument().typeParameters[0].annotationMetadata.annotationNames.containsAll(TYPE_ARGUMENT_ANNOTATIONS)

        and: 'nothing leaks into the property that was not annotated'
        testAnnotations(other.annotationMetadata).isEmpty()
        testAnnotations(other.asArgument().typeParameters[0].annotationMetadata).isEmpty()
    }

    void 'test annotating the members of a groovy property is kept in the bean definition'() {
        when:
        def definition = buildBeanDefinition('annotatemembers2.Config', '''
package annotatemembers2

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.inject.annotation.modify.MyAnnotation

@ConfigurationProperties("my.config")
class Config {
    @MyAnnotation
    List<String> webs
    List<String> other
}
''')
        def webs = definition.injectedMethods.find { it.name == 'setWebs' }
        def other = definition.injectedMethods.find { it.name == 'setOther' }

        then:
        webs.annotationMetadata.annotationNames.containsAll(MEMBER_ANNOTATIONS)
        webs.arguments[0].annotationMetadata.annotationNames.containsAll(MEMBER_ANNOTATIONS)
        webs.arguments[0].typeParameters[0].annotationMetadata.annotationNames.containsAll(TYPE_ARGUMENT_ANNOTATIONS)

        and: 'nothing leaks into the property that was not annotated'
        testAnnotations(other.annotationMetadata).isEmpty()
        testAnnotations(other.arguments[0].annotationMetadata).isEmpty()
        testAnnotations(other.arguments[0].typeParameters[0].annotationMetadata).isEmpty()
    }

    void 'test removing an annotation through the setter parameter of a groovy property'() {
        when:
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, RemovePropertyAnnotationVisitor.name)
        def introspection = buildBeanIntrospection('annotatemembers3.Test', '''
package annotatemembers3

import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.annotation.modify.MyAnnotation

@Introspected
class Test {
    @MyAnnotation
    List<String> webs
}
''')

        then:
        !introspection.getRequiredProperty('webs', List).hasAnnotation(MyAnnotation)
    }

    private static Set<String> testAnnotations(AnnotationMetadata metadata) {
        return metadata.annotationNames.findAll { it.startsWith('test.') } as Set<String>
    }

    static class AnnotatePropertyMembersVisitor implements TypeElementVisitor<Object, Object> {

        @NonNull
        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            element.beanProperties.findAll { it.hasAnnotation(MyAnnotation) }.each { it.annotate('test.Property') }
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            if (element.hasAnnotation(MyAnnotation)) {
                element.annotate('test.Field')
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (element.parameters.length == 1 && element.parameters[0].hasAnnotation(MyAnnotation)) {
                element.annotate('test.Setter')
                element.parameters[0].annotate('test.SetterParameter')
                element.parameters[0].genericType.typeArguments.values().first().annotate('test.SetterTypeArgument')
            } else if (element.parameters.length == 0 && element.hasAnnotation(MyAnnotation)) {
                element.annotate('test.Getter')
                element.genericReturnType.typeArguments.values().first().annotate('test.GetterTypeArgument')
            }
        }
    }

    static class RemovePropertyAnnotationVisitor implements TypeElementVisitor<Object, Object> {

        @NonNull
        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (element.parameters.length == 1 && element.parameters[0].hasAnnotation(MyAnnotation)) {
                element.parameters[0].removeAnnotation(MyAnnotation)
            }
        }
    }
}
