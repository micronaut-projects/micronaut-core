package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.PendingFeature

class AnnotateTypeArgSpec extends AbstractTypeElementSpec {

    void 'is always triggered without annotations'() {
        when:
            def introspection = buildBeanIntrospection('addann.AnnotateTypeArg0', '''
package addann;

import java.util.Map;

class AnnotateTypeArg0 {

 private Map<String, String> nameMap;

}

''')
        then:
            introspection.hasAnnotation(MyAnnotation)
    }

    void 'test annotating 1'() {
        when:
            def introspection = buildBeanIntrospection('addann.AnnotateTypeArg1', '''
package addann;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

class AnnotateTypeArg1 {

 private Map<@NotBlank String, String> nameMap;

}

''')
        then:
            introspection.hasAnnotation(MyAnnotation)
    }

    @PendingFeature(reason = "Annotation processor doesn't trigger for inner classes with type argument only annotation")
    void 'test annotating 2'() {
        when:
            def introspection = buildBeanIntrospection('addann.Outer1$AnnotateTypeArg2', '''
package addann;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

class Outer1 {
    static class AnnotateTypeArg2 {

        private Map<@NotBlank String, String> nameMap;

    }
}

''')
        then:
            introspection.hasAnnotation(MyAnnotation)
    }

    void 'test annotating 3'() {
        when:
            def introspection = buildBeanIntrospection('addann.$addann_Outer2$NotProcessedByVisitor', '''
package addann;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Introspected(classes = addann.Outer2.NotProcessedByVisitor.class, accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class Outer2 {
    static class NotProcessedByVisitor {

        private Map<@NotBlank String, String> hnameMap;

    }
}

''')
        then:
            introspection
            introspection.getPropertyNames().toList() == ["hnameMap"]
    }

    void 'test annotating 4'() {
        when:
            def introspection = buildBeanIntrospection('addann.$addann_Outer3$NotProcessedByVisitor2', '''
package addann;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Introspected(classNames = "addann.Outer3.NotProcessedByVisitor2", accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class Outer3 {
    static class NotProcessedByVisitor2 {

        private Map<@NotBlank String, String> hnameMap;

    }
}

''')
        then:
            introspection
            introspection.getPropertyNames().toList() == ["hnameMap"]
    }

    static class AnnotateTypeArgVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName().contains("AnnotateTypeArg")) {
                element.annotate(Introspected)
                element.annotate(MyAnnotation)
            }
        }
    }

}
