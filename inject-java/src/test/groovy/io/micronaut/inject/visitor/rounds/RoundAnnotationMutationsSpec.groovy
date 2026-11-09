package io.micronaut.inject.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.PendingFeature

import java.util.concurrent.CopyOnWriteArrayList

class RoundAnnotationMutationsSpec extends AbstractTypeElementSpec {

    void setup() {
        MutatingVisitor.EVENTS.clear()
    }

    /**
     * javac keeps the symbol of a class across rounds, and what a visitor added to the class is seen in a later
     * round, but it enters the members of the class anew every round: what a visitor added to a method, a field or
     * a parameter is kept under the member symbols of the round it was added in.
     */
    @PendingFeature(reason = 'javac enters the members of a class anew every round')
    void "test the annotations a visitor added in one round are seen on the declarations looked up in a later round"() {
        when:
        def context = buildContext('roundmutations.Target', '''
package roundmutations;

public class Target {
    private final String name;

    public Target(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String foo(String value) {
        return value;
    }
}
''')

        then:
        MutatingVisitor.EVENTS == [
                'round1 ' + MutatingVisitor.EXPECTED,
                'round2 ' + MutatingVisitor.EXPECTED
        ]

        cleanup:
        context?.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new MutatingVisitor()]
    }

    /**
     * Annotates a class, its method, the method's parameter, its property and its constructor parameter in the first
     * round, then reads them in the first round and in a second round, forced by a class generated in the first,
     * always through declarations looked up again.
     */
    static class MutatingVisitor implements TypeElementVisitor<Object, Object> {

        static final String PACKAGE = 'roundmutations'
        static final String ADDED = 'roundmutations.Added'
        static final String EXPECTED = '[class:true, method:true, parameter:true, property:true, constructorParameter:true]'
        static final List<String> EVENTS = new CopyOnWriteArrayList<>()

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == PACKAGE + '.Target') {
                element.annotate(ADDED)
                MethodElement foo = foo(element)
                foo.annotate(ADDED)
                foo.parameters[0].annotate(ADDED)
                property(element).annotate(ADDED)
                element.primaryConstructor.orElseThrow().parameters[0].annotate(ADDED)
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
            MethodElement foo = foo(target)
            return [
                    class               : target.hasDeclaredAnnotation(ADDED),
                    method              : foo.hasDeclaredAnnotation(ADDED),
                    parameter           : foo.parameters[0].hasDeclaredAnnotation(ADDED),
                    property            : property(target).hasDeclaredAnnotation(ADDED),
                    constructorParameter: target.primaryConstructor.orElseThrow().parameters[0].hasDeclaredAnnotation(ADDED)
            ].toString()
        }

        private static MethodElement foo(ClassElement element) {
            return element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named('foo')).orElseThrow()
        }

        private static PropertyElement property(ClassElement element) {
            return element.beanProperties.find { it.name == 'name' }
        }
    }
}
