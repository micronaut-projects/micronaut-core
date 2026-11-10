package io.micronaut.inject.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.concurrent.CopyOnWriteArrayList

class RoundAnnotationMutationsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package roundmutations;

public class Target {
    private final String name;
    public int count;

    public Target(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String foo(String value) {
        return value;
    }

    public String foo(int value) {
        return String.valueOf(value);
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

    /**
     * javac keeps the symbol of a class across rounds, but enters the members of the class anew every round: what a
     * visitor added to a method, a field, a constructor or a parameter must still be seen through the member symbols
     * of a later round, and an overload of the method must not see it.
     */
    void "test the annotations a visitor added in one round are seen on the declarations looked up in a later round"() {
        when:
        def context = buildContext('roundmutations.Target', SOURCE)

        then:
        MutatingVisitor.EVENTS == [
                'round1 ' + MutatingVisitor.EXPECTED,
                'round2 ' + MutatingVisitor.EXPECTED
        ]

        cleanup:
        context?.close()
    }

    void "test the annotations a visitor added in one compilation are not seen in another"() {
        given:
        buildContext('roundmutations.Target', SOURCE).close()
        MutatingVisitor.EVENTS.clear()
        MutatingVisitor.annotate = false

        when:
        buildContext('roundmutations.Target', SOURCE).close()

        then:
        MutatingVisitor.EVENTS == [
                'round1 ' + MutatingVisitor.NOT_ADDED,
                'round2 ' + MutatingVisitor.NOT_ADDED
        ]
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new MutatingVisitor()]
    }

    /**
     * Annotates a class, one of two overloaded methods and its parameter, a field, a property, the constructor and
     * its parameter in the first round, then reads them in the first round and in a second round, forced by a class
     * generated in the first, always through declarations looked up again.
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
                    writer.write('package roundmutations;\n\nclass Later {\n}\n')
                }
                EVENTS.add('round1 ' + read(context))
            } else if (element.name == PACKAGE + '.Later') {
                EVENTS.add('round2 ' + read(context))
            }
        }

        private static String read(VisitorContext context) {
            ClassElement target = context.getClassElement(PACKAGE + '.Target').orElseThrow()
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
