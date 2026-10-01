package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * A bean property is resolved for the type it is read through, so an annotation a visitor adds to a property
 * belongs to that type: a property of a super type annotated while visiting the super type must not carry the
 * annotation when the same property is read through a subclass.
 */
class InheritedPropertyAnnotationMutationSpec extends AbstractKotlinCompilerSpec {

    void 'a property annotated through a super type is not annotated through the subclass'() {
        given:
        IgnorePropsRecordingVisitor.reset()

        when:
        buildClassLoader('ignprops.A', '''
package ignprops

@IgnoreProps("p2")
open class A : B() {
    var p1: String? = null
    var p2: String? = null
}

@IgnoreProps("a2")
open class B : C() {
    var a1: String? = null
    var a2: String? = null
}

@IgnoreProps("f2")
open class C {
    var f1: String? = null
    var f2: String? = null
}

@Retention(AnnotationRetention.RUNTIME)
annotation class IgnoreProps(vararg val value: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class Ignored
''')

        then: 'each type only carries the annotations added through it'
        IgnorePropsRecordingVisitor.ignoredProperties['ignprops.C'] == ['f2']
        IgnorePropsRecordingVisitor.ignoredProperties['ignprops.B'] == ['a2']
        IgnorePropsRecordingVisitor.ignoredProperties['ignprops.A'] == ['p2']
    }

    /**
     * Annotates the properties a type names in its own {@code @IgnoreProps}, like micronaut-serialization does
     * for {@code @JsonIgnoreProperties}.
     */
    static class IgnorePropsVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            def ignored = element.stringValues('ignprops.IgnoreProps') as Set
            if (ignored.isEmpty()) {
                return
            }
            element.beanProperties
                .findAll { ignored.contains(it.name) }
                .each { it.annotate('ignprops.Ignored') }
        }
    }

    /**
     * Records, once every type was visited, the properties that carry the annotation when read through each type.
     */
    static class IgnorePropsRecordingVisitor implements TypeElementVisitor<Object, Object> {

        static List<ClassElement> elements = []
        static Map<String, List<String>> ignoredProperties = [:]

        static void reset() {
            elements = []
            ignoredProperties = [:]
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.hasAnnotation('ignprops.IgnoreProps')) {
                elements << element
            }
        }

        @Override
        void finish(VisitorContext visitorContext) {
            for (ClassElement element : elements) {
                ignoredProperties[element.name] = element.beanProperties
                    .findAll { it.hasAnnotation('ignprops.Ignored') }*.name.sort()
            }
        }
    }
}
