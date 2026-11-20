package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue

/**
 * An annotation a visitor builds with {@link AnnotationValue#builder(String)} and nests in another annotation it adds
 * to an element is completed as the outer annotation is: with the defaults of its type and its non-binding members.
 * The sibling copies of this spec live in the inject-java, inject-kotlin and inject-python test suites.
 *
 * @see CompletingVisitor
 */
class NestedAnnotationValueDefaultsSpec extends AbstractBeanDefinitionSpec {

    void 'test the annotations a visitor nests in an annotation it adds are completed'() {
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

        expect: 'the nested annotation carries the defaults and non-binding members of its type, and its own values'
        def added = CompletingVisitor.added
        added.getDefaultValues().name == 'v8'
        def nested = added.getAnnotation('cylinders').get()
        nested.intValue().asInt == 8
        nested.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nested.getDefaultValues() == [unit: 'count']

        and: 'so do the annotations nested in an array'
        def spares = added.getAnnotations('spares')
        spares*.intValue()*.asInt == [8, 2]
        spares*.stringValue('unit')*.orElse(null) == [null, 'pairs']
        spares.every { it.getDefaultValues() == [unit: 'count'] }
        spares.every { it.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE).contains('description') }

        and: 'the written metadata records the non-binding members'
        def written = definition.getAnnotationMetadata().getAnnotation('complete.Engine')
        written.getAnnotation('cylinders').get().stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        written.getAnnotations('spares').every { it.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE).contains('description') }
    }
}
