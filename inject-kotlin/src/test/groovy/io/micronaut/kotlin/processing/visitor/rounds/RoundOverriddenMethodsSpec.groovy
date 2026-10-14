package io.micronaut.kotlin.processing.visitor.rounds

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.utils.NativeElementsHelper
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.util.concurrent.CopyOnWriteArrayList

class RoundOverriddenMethodsSpec extends AbstractKotlinCompilerSpec {

    void setup() {
        OverridesRecordingVisitor.EVENTS.clear()
    }

    void "test the overridden methods of a class seen in an earlier round are found again in a later round"() {
        when:
        buildContext('''
package roundoverrides

open class Base {
    open fun foo(): String = "base"
}

open class Sub : Base() {
    override fun foo(): String = "sub"
}

class Trigger
''')

        then:"the second round answers from its own symbols, as the first round did"
        OverridesRecordingVisitor.EVENTS == [
                'round1 roundoverrides.Sub.foo overrides [roundoverrides.Base]',
                'round1 cached [processedClasses:true, overridesCache:true, pairwiseOverridesCache:true]',
                'round2 retained [processedClasses:false, overridesCache:false, pairwiseOverridesCache:false]',
                'round2 roundoverrides.Later.foo overrides [roundoverrides.Base, roundoverrides.Sub]',
                'round2 roundoverrides.Sub.foo overrides [roundoverrides.Base]'
        ]
    }

    /**
     * Records the overridden methods of {@code foo} in both rounds, and which of the native elements helper's memos
     * still hold the entries of the first round once the second round starts. KSP hands out new symbols every
     * round, so a retained entry can never be hit again, but it keeps the dead session's symbols reachable.
     */
    static class OverridesRecordingVisitor implements TypeElementVisitor<Object, Object> {

        static final String PACKAGE = 'roundoverrides'
        static final List<String> EVENTS = new CopyOnWriteArrayList<>()

        private boolean generated
        private Map<String, List<Object>> firstRoundEntries

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            switch (element.name) {
                case PACKAGE + '.Sub':
                    record('round1', element)
                    break
                case PACKAGE + '.Trigger':
                    if (!generated) {
                        generated = true
                        context.visitGeneratedSourceFile(PACKAGE, 'Later', element).orElseThrow().write { writer ->
                            writer.write('''package roundoverrides

class Later : Sub() {
    override fun foo(): String = "later"
}
''')
                        }
                    }
                    break
                case PACKAGE + '.Later':
                    Map<String, List<Object>> current = entries(context)
                    EVENTS.add('round2 retained ' + firstRoundEntries.collectEntries { name, old ->
                        [name, current[name].any { entry -> old.any { it.is(entry) } }]
                    })
                    record('round2', element)
                    record('round2', context.getClassElement(PACKAGE + '.Sub').orElseThrow())
                    break
                default:
                    break
            }
        }

        @Override
        void finishRound(VisitorContext context) {
            if (generated && firstRoundEntries == null) {
                firstRoundEntries = entries(context)
                EVENTS.add('round1 cached ' + firstRoundEntries.collectEntries { name, old -> [name, !old.isEmpty()] })
            }
        }

        private static void record(String round, ClassElement element) {
            MethodElement foo = element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named('foo')).orElseThrow()
            EVENTS.add(round + ' ' + element.name + '.foo overrides ' + foo.overriddenMethods*.declaringType*.name)
        }

        private static Map<String, List<Object>> entries(VisitorContext context) {
            Object helper = context.nativeElementsHelper
            return [
                    processedClasses      : new ArrayList<Object>(field(NativeElementsHelper, 'processedClasses', helper) as Set),
                    overridesCache        : new ArrayList<Object>((field(NativeElementsHelper, 'overridesCache', helper) as Map).keySet()),
                    pairwiseOverridesCache: new ArrayList<Object>((field(helper.getClass(), 'overridesCache', helper) as Map).keySet())
            ]
        }

        private static Object field(Class<?> type, String name, Object instance) {
            def field = type.getDeclaredField(name)
            field.accessible = true
            return field.get(instance)
        }
    }
}
