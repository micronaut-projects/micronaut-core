package io.micronaut.inject.visitor.rounds

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Groovy compilation has no processing rounds: a source a visitor generates is added to the running compilation
 * and visited there, and the declarations of the classes already visited are the same nodes. What a visitor added to
 * a member is seen when the declaration is looked up again while the generated class is visited. The sibling copies
 * of this spec live in the inject-java and inject-kotlin test suites.
 */
class RoundAnnotationMutationsSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package roundmutations

class Target {
    private final String name
    public int count

    Target(String name) {
        this.name = name
    }

    String getName() {
        return name
    }

    String foo(String value) {
        return value
    }

    String foo(int value) {
        return String.valueOf(value)
    }
}
'''

    void setup() {
        MutatingVisitor.EVENTS.clear()
        MutatingVisitor.annotate = true
    }

    void cleanup() {
        MutatingVisitor.annotate = true
    }

    void "test the annotations a visitor added are seen on the declarations looked up while a generated class is visited"() {
        when:
        buildClassLoader(SOURCE)

        then:
        MutatingVisitor.EVENTS == [
                'first ' + MutatingVisitor.EXPECTED,
                'generated ' + MutatingVisitor.EXPECTED
        ]
    }

    void "test the annotations a visitor added in one compilation are not seen in another"() {
        given:
        buildClassLoader(SOURCE)
        MutatingVisitor.EVENTS.clear()
        MutatingVisitor.annotate = false

        when:
        buildClassLoader(SOURCE)

        then:
        MutatingVisitor.EVENTS == [
                'first ' + MutatingVisitor.NOT_ADDED,
                'generated ' + MutatingVisitor.NOT_ADDED
        ]
    }

    /**
     * Annotates a class, one of two overloaded methods and its parameter, a field, a property, the constructor and
     * its parameter, then reads them right away and again while visiting a class generated from the first, always
     * through declarations looked up again.
     */
    static class MutatingVisitor implements TypeElementVisitor<Object, Object> {

        static final String PACKAGE = 'roundmutations'
        static final String ADDED = 'roundmutations.Added'
        static final String EXPECTED = '[class:true, method:true, parameter:true, overload:false, overloadParameter:false, field:true, property:true, constructor:true, constructorParameter:true]'
        static final String NOT_ADDED = '[class:false, method:false, parameter:false, overload:false, overloadParameter:false, field:false, property:false, constructor:false, constructorParameter:false]'
        static final List<String> EVENTS = new CopyOnWriteArrayList<>()
        static volatile boolean annotate = true

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == PACKAGE + '.Target') {
                if (annotate) {
                    element.annotate(ADDED)
                    MethodElement foo = fooMethod(element, 'java.lang.String')
                    foo.annotate(ADDED)
                    foo.parameters[0].annotate(ADDED)
                    field(element).annotate(ADDED)
                    property(element).annotate(ADDED)
                    constructor(element).annotate(ADDED)
                    constructor(element).parameters[0].annotate(ADDED)
                }
                context.visitGeneratedSourceFile(PACKAGE, 'Later', element).orElseThrow().write { writer ->
                    writer.write('package roundmutations\n\nclass Later extends Target {\n    Later() {\n        super("later")\n    }\n}\n')
                }
                EVENTS.add('first ' + read(context.getClassElement(PACKAGE + '.Target').orElseThrow()))
            } else if (element.name == PACKAGE + '.Later') {
                // the visitor context of a generated source does not find a class of the running compilation
                // by name, the declaration is looked up again through the superclass of the generated class
                EVENTS.add('generated ' + read(element.superType.orElseThrow()))
            }
        }

        private static String read(ClassElement target) {
            MethodElement foo = fooMethod(target, 'java.lang.String')
            MethodElement overload = fooMethod(target, 'int')
            return [
                    class               : target.hasDeclaredAnnotation(ADDED),
                    method              : foo.hasDeclaredAnnotation(ADDED),
                    parameter           : foo.parameters[0].hasDeclaredAnnotation(ADDED),
                    overload            : overload.hasDeclaredAnnotation(ADDED),
                    overloadParameter   : overload.parameters[0].hasDeclaredAnnotation(ADDED),
                    field               : field(target).hasDeclaredAnnotation(ADDED),
                    property            : property(target).hasDeclaredAnnotation(ADDED),
                    constructor         : constructor(target).hasDeclaredAnnotation(ADDED),
                    constructorParameter: constructor(target).parameters[0].hasDeclaredAnnotation(ADDED)
            ].toString()
        }

        private static MethodElement fooMethod(ClassElement element, String parameterType) {
            return element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named('foo')
                    .filter { it.parameters[0].type.name == parameterType }).orElseThrow()
        }

        private static FieldElement field(ClassElement element) {
            return element.getEnclosedElement(ElementQuery.ALL_FIELDS.onlyInstance().named('count')).orElseThrow()
        }

        private static ConstructorElement constructor(ClassElement element) {
            return element.primaryConstructor.orElseThrow() as ConstructorElement
        }

        private static PropertyElement property(ClassElement element) {
            return element.beanProperties.find { it.name == 'name' }
        }
    }
}
