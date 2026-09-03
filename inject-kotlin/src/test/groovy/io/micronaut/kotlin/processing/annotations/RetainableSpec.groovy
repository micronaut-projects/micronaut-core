package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationValue

class RetainableSpec extends AbstractKotlinCompilerSpec {

    void 'test a retainable composed annotation is attributed to the annotation that introduced it'() {
        given:
        def definition = buildBeanDefinition('retainspec.Test', '''
package retainspec

import io.micronaut.context.annotation.AliasFor
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Retainable

@Retainable
@Retention(AnnotationRetention.RUNTIME)
annotation class SizeLike(val min: Int = 0, val max: Int = 100)

@SizeLike(min = 5)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposedA(
    @get:AliasFor(annotationName = "retainspec.SizeLike", member = "min", applyDefault = true)
    val min: Int = 5
)

@SizeLike(max = 50)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposedB(
    @get:AliasFor(annotationName = "retainspec.SizeLike", member = "max", applyDefault = true)
    val max: Int = 50
)

@Bean
class Test {

    @ComposedA(min = 3)
    @ComposedB(max = 9)
    @Executable
    fun getValue(): String = ""
}
''')
        def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        expect: 'each composed annotation reports the occurrence it introduced'
        sizeOf(metadata.getAnnotation("retainspec.ComposedA")) == [[min: 3]]
        sizeOf(metadata.getAnnotation("retainspec.ComposedB")) == [[max: 9]]
    }

    private static List<Map> sizeOf(AnnotationValue<?> annotationValue) {
        annotationValue.getStereotypes()
                .findAll { it.getAnnotationName() == "retainspec.SizeLike" }
                .collect { it.getValues() }
    }
}
