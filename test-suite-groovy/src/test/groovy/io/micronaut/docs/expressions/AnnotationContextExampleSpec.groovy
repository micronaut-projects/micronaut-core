package io.micronaut.docs.expressions

import io.micronaut.context.BeanContext
import io.micronaut.inject.BeanDefinition
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AnnotationContextExampleSpec extends Specification {
    @Shared
    @AutoCleanup
    BeanContext beanContext = BeanContext.run()
    void "testAnnotationContextEvaluation"() {
        given:
        BeanDefinition<Example> beanDefinition = beanContext.getBeanDefinition(Example)
        String val = beanDefinition.stringValue(CustomAnnotation).orElse(null)

        expect:
        "first valuesecond value" == val
    }
}
