package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class RemapMemberAnnotationToAliasForSpec extends AbstractKotlinCompilerSpec {

    // NOTE: repeatable stereotype containers on Kotlin annotation classes are not extracted into
    // the metadata by the KSP builder (independently of aliasing), so the constraintIndex scenario
    // covered by the Java and Groovy variants of this spec cannot be replicated here yet.

    void 'test member annotation transformed to AliasFor overrides the stereotype member'() {
        given:
            def definition = buildBeanDefinition('addann.OverridesTest', '''
package addann

import io.micronaut.annotation.mapping.MyOverrides
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable

@Retention(AnnotationRetention.RUNTIME)
annotation class SizeLike(val min: Int = 0, val max: Int = 100, val message: String = "size")

@SizeLike(min = 10)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposedSize(
    @get:MyOverrides(constraint = SizeLike::class)
    val min: Int = 5,
    @get:MyOverrides(constraint = SizeLike::class, name = "message")
    val sizeMessage: String = "composed size"
)

@Bean
class OverridesTest {

    @ComposedSize(min = 3)
    @Executable
    fun getValue(): String = ""
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        expect: 'the explicitly set member overrides the declared stereotype value'
            metadata.hasStereotype('addann.SizeLike')
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 3

        and: 'the default of an overriding member applies too'
            metadata.stringValue('addann.SizeLike', 'message').get() == 'composed size'
    }

    void 'test the default value of the overriding member replaces the declared stereotype value'() {
        given:
            def definition = buildBeanDefinition('addann.OverridesTest', '''
package addann

import io.micronaut.annotation.mapping.MyOverrides
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable

@Retention(AnnotationRetention.RUNTIME)
annotation class SizeLike(val min: Int = 0, val max: Int = 100)

@SizeLike(min = 10)
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposedSize(
    @get:MyOverrides(constraint = SizeLike::class)
    val min: Int = 5
)

@Bean
class OverridesTest {

    @ComposedSize
    @Executable
    fun getValue(): String = ""
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        expect:
            metadata.hasStereotype('addann.SizeLike')
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 5
    }
}
