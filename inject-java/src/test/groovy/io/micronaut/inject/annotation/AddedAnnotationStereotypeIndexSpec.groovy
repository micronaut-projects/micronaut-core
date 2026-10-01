package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.InterceptorBinding
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * An annotation a visitor adds to an annotation type reaches the classes declaring that annotation together with
 * the annotations it is itself meta-annotated with. A {@code @Retainable} annotation additionally keeps the
 * occurrences composing it in a reserved member; that member holds the retainable part of the closure only, so
 * reading it back as the stereotypes the annotation was written with would drop everything else it composes.
 */
class AddedAnnotationStereotypeIndexSpec extends AbstractTypeElementSpec {

    void "an annotation added to an annotation type keeps its own stereotypes in the index"() {
        given:
        def definition = buildBeanDefinition('addedstereotype.Test', '''
package addedstereotype;

import io.micronaut.inject.annotation.MyDeclaredStereotype;
import jakarta.inject.Singleton;

@Singleton
@MyDeclaredStereotype
class Test {}
''')

        expect: "the added annotation is there"
        definition.hasStereotype(MyAddedScope.name)

        and: "so are the annotations it is meta-annotated with, the retainable one and the one that is not"
        definition.getAnnotationNamesByStereotype(MyScopeMarker.name) == [MyAddedScope.name]
        definition.getAnnotationNamesByStereotype(MyOtherMarker.name) == [MyAddedScope.name]

        and: "the retainable occurrence is still retained by the annotation composing it"
        definition.getAnnotation(MyAddedScope.name).stereotypes*.annotationName == [MyScopeMarker.name]
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new ScopeAddingVisitor()]
    }

    static class ScopeAddingVisitor implements TypeElementVisitor<Object, MyDeclaredStereotype> {
        @Override
        void start(VisitorContext visitorContext) {
            visitorContext.getClassElement(MyDeclaredStereotype).ifPresent({ ClassElement ce ->
                ce.annotate(MyAddedScope)
            })
        }
    }
}

// The one core annotation carrying the marker, which makes everything composing it retainable
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@interface MyScopeMarker {}

@Retention(RetentionPolicy.RUNTIME)
@interface MyOtherMarker {}

@MyScopeMarker
@MyOtherMarker
@Retention(RetentionPolicy.RUNTIME)
@interface MyAddedScope {}

@Retention(RetentionPolicy.RUNTIME)
@interface MyDeclaredStereotype {}
