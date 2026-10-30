package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue

/**
 * {@link io.micronaut.inject.visitor.VisitorContext#completeAnnotationValue(AnnotationValue)} completes a value a
 * visitor builds itself, and the values nested in its members, the way an annotation added to an element is
 * completed. The sibling copies of this spec live in the inject-java, inject-groovy and inject-python test suites.
 *
 * <p>The annotation types annotate declarations in the sources: under KSP an annotation type that only appears
 * nested in another annotation reports no defaults, nor the non-binding members that have one.</p>
 *
 * @see CompletingVisitor
 */
class CompleteAnnotationValueSpec extends AbstractKotlinCompilerSpec {

    void 'test a value a visitor builds is completed with the defaults and non-binding members of its type'() {
        given:
        CompletingVisitor.reset()
        def definition = buildBeanDefinition('complete.Test', '''
package complete

import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Singleton

@Singleton
class Test

@Engine(cylinders = Cylinders(4))
class Used

@Cylinders(6)
class UsedDirectly

@Retention(AnnotationRetention.RUNTIME)
annotation class Cylinders(
    val value: Int,
    @get:NonBinding
    val description: String = "",
    val unit: String = "count"
)

@Retention(AnnotationRetention.RUNTIME)
annotation class Engine(
    val cylinders: Cylinders,
    val spares: Array<Cylinders> = [],
    val name: String = "v8"
)
''')

        expect: 'adding the built value to an element completes the value itself but not the values nested in it'
        def added = definition.getAnnotationMetadata().getAnnotation('complete.Engine')
        added.getDefaultValues().name == 'v8'
        def addedNested = added.getAnnotation('cylinders').get()
        !addedNested.contains(AnnotationUtil.NON_BINDING_ATTRIBUTE)
        !addedNested.getDefaultValues()

        and: 'the completed value carries the defaults of its type'
        def completed = CompletingVisitor.completed
        completed.getDefaultValues().name == 'v8'

        and: 'and so does the value nested in a member, with its non-binding members, keeping its explicit values'
        def nested = completed.getAnnotation('cylinders').get()
        nested.intValue().asInt == 8
        nested.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nested.getDefaultValues() == [unit: 'count']

        and: 'and the values nested in an array'
        def spares = completed.getAnnotations('spares')
        spares*.intValue()*.asInt == [8, 2]
        spares*.stringValue('unit')*.orElse(null) == [null, 'pairs']
        spares.every { it.getDefaultValues() == [unit: 'count'] }
        spares.every { it.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE).contains('description') }

        and: 'a value whose type cannot be resolved is returned as it is'
        CompletingVisitor.unresolvedCompleted.is(CompletingVisitor.unresolved)

        and: 'a complete value is returned as it is'
        CompletingVisitor.completedAgain.is(CompletingVisitor.completed)
    }
}
