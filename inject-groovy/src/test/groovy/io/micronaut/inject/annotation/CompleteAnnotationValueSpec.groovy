package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue

/**
 * {@link io.micronaut.inject.visitor.VisitorContext#completeAnnotationValue(AnnotationValue)} completes a value a
 * visitor builds itself, and the values nested in its members, the way an annotation added to an element is
 * completed. The sibling copies of this spec live in the inject-java, inject-kotlin and inject-python test suites.
 *
 * @see CompletingVisitor
 */
class CompleteAnnotationValueSpec extends AbstractBeanDefinitionSpec {

    void 'test a value a visitor builds is completed with the defaults and non-binding members of its type'() {
        given:
        CompletingVisitor.reset()
        def definition = buildBeanDefinition('complete.Test', '''
package complete

import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Singleton
import java.lang.annotation.Retention
import static java.lang.annotation.RetentionPolicy.RUNTIME

@Singleton
class Test {
}

@Engine(cylinders = @Cylinders(4))
class Used {
}

@Retention(RUNTIME)
@interface Cylinders {
    int value()

    @NonBinding
    String description() default ""

    String unit() default "count"
}

@Retention(RUNTIME)
@interface Engine {
    Cylinders cylinders()

    Cylinders[] spares() default []

    String name() default "v8"
}
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
