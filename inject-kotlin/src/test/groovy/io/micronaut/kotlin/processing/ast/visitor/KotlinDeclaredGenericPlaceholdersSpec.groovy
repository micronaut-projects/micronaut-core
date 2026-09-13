package io.micronaut.kotlin.processing.ast.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * Parity with javac and Groovy: the declared generic placeholders of a parameterized use are the variables the
 * class declares, not the arguments of the use.
 */
class KotlinDeclaredGenericPlaceholdersSpec extends AbstractKotlinCompilerSpec {

    void "test the declared generic placeholders of a parameterized use are the declared variables"() {
        given:
        PlaceholdersVisitor.RESULTS.clear()
        buildClassElement('test.PlaceholdersBean', '''
package test

class PlaceholdersBean<T : CharSequence> {
    var list: MutableList<String>? = null
    var map: MutableMap<String, Int>? = null
    var variable: T? = null
}
''')
        def r = PlaceholdersVisitor.RESULTS

        expect:
        r['class'] == ['T']
        r['list.bound'] == ['java.lang.String']
        r['list.declared'] == ['E']
        r['map.declared'] == ['K', 'V']
    }

    static class PlaceholdersVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Object> RESULTS = [:]

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name != 'test.PlaceholdersBean') {
                return
            }
            RESULTS['class'] = element.getDeclaredGenericPlaceholders()*.variableName
            def list = element.getFields().find { it.name == 'list' }.getType()
            RESULTS['list.bound'] = list.getBoundGenericTypes()*.name
            RESULTS['list.declared'] = list.getDeclaredGenericPlaceholders()*.variableName
            RESULTS['map.declared'] = element.getFields().find { it.name == 'map' }.getType().getDeclaredGenericPlaceholders()*.variableName
        }
    }
}
