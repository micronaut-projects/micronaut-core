package io.micronaut.inject.qualifiers

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue

class NonBindingMembersRecordedSpec extends AbstractBeanDefinitionSpec {

    void 'test an occurrence leaving a @NonBinding member at its default records it'() {
        given:
        def definition = buildBeanDefinition('nonbindingdefault.Test', '''
package nonbindingdefault

import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.lang.annotation.Retention
import static java.lang.annotation.RetentionPolicy.RUNTIME

@Cylinders(8)
@Singleton
class Test {
}

@Qualifier
@Retention(RUNTIME)
@interface Cylinders {
    int value()

    @NonBinding
    String description() default ""
}
''')
        def metadata = definition.getAnnotationMetadata()

        expect:
        nonBinding(metadata.getAnnotation('nonbindingdefault.Cylinders')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
        nonBinding(metadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE] as Set
    }

    void 'test two annotation interfaces whose members are annotated with each other'() {
        given:
        def definition = buildBeanDefinition('nonbindingmutual.Test', '''
package nonbindingmutual

import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.lang.annotation.Retention
import static java.lang.annotation.RetentionPolicy.RUNTIME

@Left(1)
@Right(2)
@Singleton
class Test {
}

@Qualifier
@Retention(RUNTIME)
@interface Left {
    @Right(0)
    int value()

    @NonBinding
    @Right(0)
    String note() default ""
}

@Qualifier
@Retention(RUNTIME)
@interface Right {
    @Left(0)
    int value()

    @NonBinding
    @Left(0)
    String note() default ""
}
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
