package io.micronaut.python.annotation.processing.test.rounds

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.python.processing.element.AbstractPythonClassElement

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Python classes of a compilation are visited in one pass over one model: there is no later processing round in
 * which their declarations are entered anew. What a visitor added to a member is seen when the declaration is looked
 * up again while a later class is visited, and once every class was visited. The sibling copies of this spec live
 * in the inject-java, inject-kotlin and inject-groovy test suites.
 */
class RoundAnnotationMutationsSpec extends AbstractPythonTypeElementSpec {

    void setup() {
        MutatingVisitor.EVENTS.clear()
    }

    void "test the annotations a visitor added are seen on the declarations looked up later in the compilation"() {
        when:
        def context = buildContext('''
from jakarta.inject import Singleton

class RoundTarget:
    count: int = 0

    def __init__(self, name: str):
        self.name = name

    def foo(self, value: str) -> str:
        return value

@Singleton
class RoundLater:
    pass
''')

        then:
        MutatingVisitor.EVENTS == [
                'first ' + MutatingVisitor.EXPECTED,
                'later ' + MutatingVisitor.EXPECTED,
                'finish ' + MutatingVisitor.EXPECTED
        ]

        cleanup:
        context?.close()
    }

    /**
     * Annotates a class, its method and the method's parameter, a field, the constructor and its parameter while
     * visiting the class, then reads them right away, while visiting a later class and once every class was visited,
     * always through declarations looked up again.
     */
    static class MutatingVisitor implements TypeElementVisitor<Object, Object> {

        static final String TARGET = 'python.RoundTarget'
        static final String ADDED = 'roundmutations.Added'
        static final String EXPECTED = '[class:true, method:true, parameter:true, field:true, constructor:true, constructorParameter:true]'
        static final List<String> EVENTS = new CopyOnWriteArrayList<>()
        private boolean visited

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (!(element instanceof AbstractPythonClassElement)) {
                // the generated Java classes are visited after the Python ones
                return
            }
            if (element.name == TARGET) {
                visited = true
                element.annotate(ADDED)
                MethodElement foo = fooMethod(element)
                foo.annotate(ADDED)
                foo.parameters[0].annotate(ADDED)
                field(element).annotate(ADDED)
                element.primaryConstructor.orElseThrow().annotate(ADDED)
                element.primaryConstructor.orElseThrow().parameters[0].annotate(ADDED)
                EVENTS.add('first ' + read(context))
            } else if (element.name == 'python.RoundLater' && visited) {
                EVENTS.add('later ' + read(context))
            }
        }

        @Override
        void finish(VisitorContext context) {
            if (visited) {
                // The visitor is registered for every compilation of the module: read the classes of this one
                visited = false
                EVENTS.add('finish ' + read(context))
            }
        }

        private static String read(VisitorContext context) {
            ClassElement target = context.getClassElement(TARGET).orElseThrow()
            MethodElement foo = fooMethod(target)
            MethodElement constructor = target.primaryConstructor.orElseThrow()
            return [
                    class               : target.hasDeclaredAnnotation(ADDED),
                    method              : foo.hasDeclaredAnnotation(ADDED),
                    parameter           : foo.parameters[0].hasDeclaredAnnotation(ADDED),
                    field               : field(target).hasDeclaredAnnotation(ADDED),
                    constructor         : constructor.hasDeclaredAnnotation(ADDED),
                    constructorParameter: constructor.parameters[0].hasDeclaredAnnotation(ADDED)
            ].toString()
        }

        private static MethodElement fooMethod(ClassElement element) {
            return element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named('foo')).orElseThrow()
        }

        private static FieldElement field(ClassElement element) {
            return element.getEnclosedElement(ElementQuery.ALL_FIELDS.named('count')).orElseThrow()
        }
    }
}
