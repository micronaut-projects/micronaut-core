package io.micronaut.kotlin.processing.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.concurrent.CopyOnWriteArrayList

class RoundAnnotationMutationsSpec extends AbstractKotlinCompilerSpec {

    void setup() {
        MutatingVisitor.EVENTS.clear()
        MutatingVisitor.annotate = true
    }

    void cleanup() {
        MutatingVisitor.annotate = true
    }

    void "test the annotations a visitor added in one round are seen on the declarations looked up in a later round"() {
        when:
        def context = buildContext('''
package roundmutations

open class Target(val name: String) {
    open fun foo(value: String): String = value
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

    void "test the annotations a visitor added in one compilation are not seen in another"() {
        given:
        def source = '''
package roundmutations

open class Target(val name: String) {
    open fun foo(value: String): String = value
}
'''
        buildContext(source).close()
        MutatingVisitor.EVENTS.clear()
        MutatingVisitor.annotate = false

        when:
        buildContext(source).close()

        then:
        MutatingVisitor.EVENTS == [
                'round1 ' + MutatingVisitor.NOT_ADDED,
                'round2 ' + MutatingVisitor.NOT_ADDED
        ]
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
        static final String NOT_ADDED = '[class:false, method:false, parameter:false, property:false, constructorParameter:false]'
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
                    MethodElement foo = foo(element)
                    foo.annotate(ADDED)
                    foo.parameters[0].annotate(ADDED)
                    property(element).annotate(ADDED)
                    element.primaryConstructor.orElseThrow().parameters[0].annotate(ADDED)
                }
                context.visitGeneratedSourceFile(PACKAGE, 'Later', element).orElseThrow().write { writer ->
                    writer.write('package roundmutations\n\nclass Later\n')
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
