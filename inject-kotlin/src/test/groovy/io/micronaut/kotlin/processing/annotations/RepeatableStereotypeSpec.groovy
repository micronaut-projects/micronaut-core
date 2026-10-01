package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class RepeatableStereotypeSpec extends AbstractKotlinCompilerSpec {

    void 'test repeated annotation declared with JvmRepeatable applied directly to a method'() {
        given:
            def definition = buildBeanDefinition('addann.DirectTest', '''
package addann

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable

@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(PatternLikeList::class)
annotation class PatternLike(val regexp: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class PatternLikeList(val value: Array<PatternLike>)

@Bean
class DirectTest {

    @PatternLike(regexp = ".....")
    @PatternLike(regexp = "bar")
    @Executable
    fun getValue(): String = ""
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        when:
            def patternType = (Class) definition.getClass().getClassLoader().loadClass('addann.PatternLike')
            def patterns = metadata.getAnnotationValuesByType(patternType)

        then: 'both occurrences are retained inside the repeatable container'
            metadata.hasAnnotation('addann.PatternLikeList')
            patterns.size() == 2
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == 'bar'
    }

    void 'test repeated annotation stereotype declared with JvmRepeatable'() {
        given:
            def definition = buildBeanDefinition('addann.RepeatedTest', '''
package addann

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable

@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(PatternLikeList::class)
annotation class PatternLike(val regexp: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class PatternLikeList(val value: Array<PatternLike>)

@PatternLike(regexp = ".....")
@PatternLike(regexp = "bar")
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposedZip

@Bean
class RepeatedTest {

    @ComposedZip
    @Executable
    fun getValue(): String = ""
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        when:
            def patternType = (Class) definition.getClass().getClassLoader().loadClass('addann.PatternLike')
            def patterns = metadata.getAnnotationValuesByType(patternType)

        then: 'both declared occurrences of the repeatable stereotype are retained'
            metadata.hasStereotype('addann.PatternLikeList')
            patterns.size() == 2
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == 'bar'
    }

    void 'test explicit container annotation stereotype'() {
        given:
            def definition = buildBeanDefinition('addann.ContainerTest', '''
package addann

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable

@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(PatternLikeList::class)
annotation class PatternLike(val regexp: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class PatternLikeList(val value: Array<PatternLike>)

@PatternLikeList(value = [PatternLike(regexp = "....."), PatternLike(regexp = "bar")])
@Retention(AnnotationRetention.RUNTIME)
annotation class ComposedZip

@Bean
class ContainerTest {

    @ComposedZip
    @Executable
    fun getValue(): String = ""
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        when:
            def patternType = (Class) definition.getClass().getClassLoader().loadClass('addann.PatternLike')
            def patterns = metadata.getAnnotationValuesByType(patternType)

        then: 'an explicitly declared container behaves the same as repeated annotations'
            metadata.hasStereotype('addann.PatternLikeList')
            patterns.size() == 2
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == 'bar'
    }
}
