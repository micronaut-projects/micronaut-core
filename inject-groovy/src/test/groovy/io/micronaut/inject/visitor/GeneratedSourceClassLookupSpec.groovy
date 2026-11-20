package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement

import java.util.concurrent.CopyOnWriteArrayList

/**
 * A visitor that runs for a source generated during the compilation looks up the classes of the compilation by name.
 * The sources already compiled through the phase the generated source is added in are no longer walked by the
 * compilation unit, and the class node resolver only knows the classes of the class loader.
 */
class GeneratedSourceClassLookupSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        LookupVisitor.EVENTS.clear()
    }

    void "test the classes of the compilation are found by name while a generated source is visited"() {
        when:
        buildClassLoader('''
package generatedlookup

class Target {
    static class Inner {
    }
}
''')

        then:
        LookupVisitor.EVENTS == [
                'Target: Target=generatedlookup.Target, Inner=generatedlookup.Target$Inner, Later=none',
                'Later: Target=generatedlookup.Target, Inner=generatedlookup.Target$Inner, Later=generatedlookup.Later'
        ]
    }

    static class LookupVisitor implements TypeElementVisitor<Object, Object> {

        static final String PACKAGE = 'generatedlookup'
        static final List<String> EVENTS = new CopyOnWriteArrayList<>()

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == PACKAGE + '.Target') {
                context.visitGeneratedSourceFile(PACKAGE, 'Later', element).orElseThrow().write { writer ->
                    writer.write('package generatedlookup\n\nclass Later {\n}\n')
                }
                EVENTS.add('Target: ' + lookup(context))
            } else if (element.name == PACKAGE + '.Later') {
                EVENTS.add('Later: ' + lookup(context))
            }
        }

        private static String lookup(VisitorContext context) {
            return ['Target', 'Target$Inner', 'Later'].collect { simpleName ->
                String found = context.getClassElement(PACKAGE + '.' + simpleName).map { it.name }.orElse('none')
                return simpleName.replace('Target$', '') + '=' + found
            }.join(', ')
        }
    }
}
