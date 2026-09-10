package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * A bean property is resolved for the type it is read through, so an annotation a visitor adds to a property
 * belongs to that type: a property of a super type annotated while visiting the super type must not carry the
 * annotation when the same property is read through a subclass. This is what micronaut-serialization relies on
 * to apply only the nearest {@code @JsonIgnoreProperties}.
 */
class InheritedPropertyAnnotationMutationSpec extends AbstractTypeElementSpec {

    void 'a property annotated through a super type is not annotated through the subclass'() {
        given:
        RecordingVisitor.reset()

        when:
        buildClassLoader('test.A', '''
package test;

import java.lang.annotation.*;

@IgnoreProps("p2")
class A extends B {
    private String p1;
    private String p2;
    public String getP1() { return p1; }
    public void setP1(String p1) { this.p1 = p1; }
    public String getP2() { return p2; }
    public void setP2(String p2) { this.p2 = p2; }
}

@IgnoreProps("a2")
class B extends C {
    private String a1;
    private String a2;
    public String getA1() { return a1; }
    public void setA1(String a1) { this.a1 = a1; }
    public String getA2() { return a2; }
    public void setA2(String a2) { this.a2 = a2; }
}

@IgnoreProps("f2")
class C {
    private String f1;
    private String f2;
    public String getF1() { return f1; }
    public void setF1(String f1) { this.f1 = f1; }
    public String getF2() { return f2; }
    public void setF2(String f2) { this.f2 = f2; }
}

@Retention(RetentionPolicy.RUNTIME)
@interface IgnoreProps {
    String[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Ignored {
}
''')

        then: 'each type only carries the annotations added through it'
        RecordingVisitor.ignoredProperties['test.C'] == ['f2']
        RecordingVisitor.ignoredProperties['test.B'] == ['a2']
        RecordingVisitor.ignoredProperties['test.A'] == ['p2']

        and: 'a field read through the type whose property was annotated carries the annotation'
        RecordingVisitor.ignoredFields['test.C'] == ['f2']
        RecordingVisitor.ignoredFields['test.B'] == ['a2']
        RecordingVisitor.ignoredFields['test.A'] == ['p2']
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new IgnorePropsVisitor(), new RecordingVisitor()]
    }

    /**
     * Annotates the properties a type names in its own {@code @IgnoreProps}, like micronaut-serialization does
     * for {@code @JsonIgnoreProperties}.
     */
    static class IgnorePropsVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            def ignored = element.stringValues('test.IgnoreProps') as Set
            element.beanProperties
                .findAll { ignored.contains(it.name) }
                .each { it.annotate('test.Ignored') }
        }
    }

    /**
     * Records, once every type was visited, the properties and the fields that carry the annotation when read
     * through each type.
     */
    static class RecordingVisitor implements TypeElementVisitor<Object, Object> {

        static List<ClassElement> elements
        static Map<String, List<String>> ignoredProperties
        static Map<String, List<String>> ignoredFields

        static void reset() {
            elements = []
            ignoredProperties = [:]
            ignoredFields = [:]
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.hasAnnotation('test.IgnoreProps')) {
                elements << element
            }
        }

        @Override
        void finish(VisitorContext visitorContext) {
            for (ClassElement element : elements) {
                ignoredProperties[element.name] = element.beanProperties
                    .findAll { it.hasAnnotation('test.Ignored') }*.name.sort()
                ignoredFields[element.name] = element.getEnclosedElements(ElementQuery.ALL_FIELDS)
                    .findAll { it.hasAnnotation('test.Ignored') }*.name.sort()
            }
        }
    }
}
