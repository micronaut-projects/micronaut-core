package io.micronaut.kotlin.processing.qualifiers

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue

class NonBindingMembersRecordedSpec extends AbstractKotlinCompilerSpec {

    void 'test an occurrence leaving a @NonBinding member at its default records it'() {
        given:
        def definition = buildBeanDefinition('nonbindingdefault.Test', '''
package nonbindingdefault

import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Qualifier
import jakarta.inject.Singleton

@Cylinders(8)
@Singleton
class Test

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Cylinders(
    val value: Int,
    @get:NonBinding
    val description: String = ""
)
''')
        def metadata = definition.getAnnotationMetadata()

        expect:
        nonBinding(metadata.getAnnotation('nonbindingdefault.Cylinders')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(metadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        definition.declaredQualifier.qualifierAnn.memberNames == ['value'] as Set
    }

    void 'test two annotation interfaces whose members are annotated with each other'() {
        given:
        def definition = buildBeanDefinition('nonbindingmutual.Test', '''
package nonbindingmutual

import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Qualifier
import jakarta.inject.Singleton

@Left(1)
@Right(2)
@Singleton
class Test

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Left(
    @get:Right(0)
    val value: Int,
    @get:NonBinding
    @get:Right(0)
    val note: String = ""
)

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Right(
    @get:Left(0)
    val value: Int,
    @get:NonBinding
    @get:Left(0)
    val note: String = ""
)
''')
        def metadata = definition.getAnnotationMetadata()

        expect:
        nonBinding(metadata.getAnnotation('nonbindingmutual.Left')) == ['note', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(metadata.getAnnotation('nonbindingmutual.Right')) == ['note', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
    }

    private static Set<String> nonBinding(AnnotationValue<?> annotation) {
        annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as Set
    }
}
