package io.micronaut.kotlin.processing.beans.builder

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.visitor.rounds.RegisteredRecord

class AssociatedBeanSpec extends AbstractKotlinCompilerSpec {

    void "test @Import makes a bean of a compiled class"() {
        given:
        def context = buildContext('''
package kimport

import io.micronaut.context.annotation.Import

@Import(classes = [io.micronaut.inject.visitor.rounds.RegisteredRecord::class])
class Imports
''')

        expect:"the definition is named after the importer"
        context.getBean(RegisteredRecord) instanceof RegisteredRecord
        context.getBeanDefinition(RegisteredRecord).getClass().name == 'kimport.$Imports$RegisteredRecord0$Definition'

        cleanup:
        context.close()
    }

    void "test an associated bean produces the beans of its methods"() {
        given:
        def context = buildContext('''
package kbeanbuilder

class Name(val value: String)

class Producer {
    fun name(): Name = Name("produced")
}
''')

        expect:
        context.getBean(context.classLoader.loadClass('kbeanbuilder.Producer'))
        context.getBean(context.classLoader.loadClass('kbeanbuilder.Name')).value == 'produced'

        cleanup:
        context.close()
    }

    /**
     * Adds a bean of {@code kbeanbuilder.Producer}, which produces the beans its methods return.
     */
    static class ProducingVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'kbeanbuilder.Producer') {
                element.addAssociatedBean(element).produceBeans(ElementQuery.ALL_METHODS.onlyDeclared())
            }
        }
    }
}
