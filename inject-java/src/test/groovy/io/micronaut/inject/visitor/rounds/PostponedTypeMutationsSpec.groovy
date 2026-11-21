package io.micronaut.inject.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.concurrent.CopyOnWriteArrayList

class PostponedTypeMutationsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package postponedmutations;

public class Target {
    public int count;

    public Target(String name) {
    }

    public String foo(String value) {
        return value;
    }
}
'''

    void setup() {
        PostponingVisitor.EVENTS.clear()
    }

    /**
     * A type postponed to the next round is visited again from scratch: what a visitor added to its members in the
     * round that postponed it is dropped together with what it added to the type, although the members are looked up
     * through the symbols of a later round.
     */
    void "test the annotations a visitor added to the members of a postponed type are dropped before it is visited again"() {
        when:
        def context = buildContext('postponedmutations.Target', SOURCE)

        then:
        PostponingVisitor.EVENTS == [
                'round1 [class:true, method:true, parameter:true, field:true, constructorParameter:true]',
                'round2 [class:false, method:false, parameter:false, field:false, constructorParameter:false]'
        ]

        cleanup:
        context?.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new PostponingVisitor()]
    }

    /**
     * Annotates the type, a method and its parameter, a field and a constructor parameter, then postpones the type to
     * the next round, forced by a class generated in the first, and reads the declarations again when the type is
     * visited in that round.
     */
    static class PostponingVisitor implements TypeElementVisitor<Object, Object> {

        static final String PACKAGE = 'postponedmutations'
        static final String ADDED = 'postponedmutations.Added'
        static final List<String> EVENTS = new CopyOnWriteArrayList<>()

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name != PACKAGE + '.Target') {
                return
            }
            if (EVENTS.isEmpty()) {
                element.annotate(ADDED)
                MethodElement foo = fooMethod(element)
                foo.annotate(ADDED)
                foo.parameters[0].annotate(ADDED)
                field(element).annotate(ADDED)
                constructor(element).parameters[0].annotate(ADDED)
                context.visitGeneratedSourceFile(PACKAGE, 'Later', element).orElseThrow().write { writer ->
                    writer.write('package postponedmutations;\n\nclass Later {\n}\n')
                }
                EVENTS.add('round1 ' + read(context))
                throw new ElementPostponedToNextRoundException(element)
            }
            EVENTS.add('round2 ' + read(context))
        }

        private static String read(VisitorContext context) {
            ClassElement target = context.getClassElement(PACKAGE + '.Target').orElseThrow()
            MethodElement foo = fooMethod(target)
            return [
                    class               : target.hasDeclaredAnnotation(ADDED),
                    method              : foo.hasDeclaredAnnotation(ADDED),
                    parameter           : foo.parameters[0].hasDeclaredAnnotation(ADDED),
                    field               : field(target).hasDeclaredAnnotation(ADDED),
                    constructorParameter: constructor(target).parameters[0].hasDeclaredAnnotation(ADDED)
            ].toString()
        }

        private static MethodElement fooMethod(ClassElement element) {
            return element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named('foo')).orElseThrow()
        }

        private static FieldElement field(ClassElement element) {
            return element.getEnclosedElement(ElementQuery.ALL_FIELDS.onlyInstance().named('count')).orElseThrow()
        }

        private static ConstructorElement constructor(ClassElement element) {
            return element.primaryConstructor.orElseThrow() as ConstructorElement
        }
    }
}
