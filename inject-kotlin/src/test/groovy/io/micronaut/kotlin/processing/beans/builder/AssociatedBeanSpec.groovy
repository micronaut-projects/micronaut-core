package io.micronaut.kotlin.processing.beans.builder

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.visitor.rounds.RegisteredRecord

import java.util.function.Predicate

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

    void "test an associated bean produces the beans of its fields"() {
        given:
        def context = buildContext('''
package kfieldbuilder

class Title(val value: String)

@Deprecated("removed from the bean")
class Producer {
    @JvmField
    val title = Title("from a field")
}
''')
        def producerType = context.classLoader.loadClass('kfieldbuilder.Producer')

        expect:
        context.getBean(context.classLoader.loadClass('kfieldbuilder.Title')).value == 'from a field'

        and:"the annotations removed from the builder are not on the definition"
        !context.getBeanDefinition(producerType).hasAnnotation(Deprecated)

        cleanup:
        context.close()
    }

    void "test a method adds an associated bean originating from it"() {
        given:
        def context = buildContext('''
package kmethodorigin

class Origin {
    fun origin() {}
}
''')

        expect:"the definition is named after the class declaring the method"
        context.getBeanDefinition(RegisteredRecord).getClass().name == 'kmethodorigin.$Origin$RegisteredRecord0$Definition'

        cleanup:
        context.close()
    }

    void "test an aggregating visitor cannot add an associated bean"() {
        when:
        buildContext('''
package kaggregating

class Origin
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Cannot add bean definition using addAssociatedBean(..) from a AGGREGATING TypeElementVisitor')
    }

    void "test a visitor failing to finish its round fails the compilation"() {
        when:
        buildContext('''
package kfailinground

class Origin
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Error finishing the round of type visitor')
        e.message.contains('round failed')
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
            } else if (element.name == 'kfieldbuilder.Producer') {
                element.addAssociatedBean(element)
                        .removeAnnotation(Deprecated.name)
                        .removeStereotype(Deprecated.name)
                        .removeAnnotationIf({ AnnotationValue<?> value -> value.annotationName == Deprecated.name } as Predicate)
                        .produceBeans(ElementQuery.ALL_FIELDS.onlyDeclared())
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (element.declaringType.name == 'kmethodorigin.Origin' && element.name == 'origin') {
                element.addAssociatedBean(context.getClassElement(RegisteredRecord).get())
            }
        }
    }

    /**
     * Adds a bean from an aggregating visitor, which fails.
     */
    static class AggregatingVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.AGGREGATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'kaggregating.Origin') {
                element.addAssociatedBean(element)
            }
        }
    }

    /**
     * Fails as it finishes a round that contained {@code kfailinground.Origin}.
     */
    static class FailingRoundVisitor implements TypeElementVisitor<Object, Object> {

        private boolean visited

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'kfailinground.Origin') {
                visited = true
            }
        }

        @Override
        void finishRound(VisitorContext visitorContext) {
            if (visited) {
                visited = false
                throw new IllegalStateException('round failed')
            }
        }
    }
}
