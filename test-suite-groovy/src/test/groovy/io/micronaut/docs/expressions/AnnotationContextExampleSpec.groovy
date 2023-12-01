package io.micronaut.docs.expressions

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AnnotationContextExampleSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext beanContext = ApplicationContext.run()

    void "testAnnotationContextEvaluation"() {
        given:
        BeanDefinition<Example> beanDefinition = beanContext.getBeanDefinition(Example)
        String val = beanDefinition.stringValue(CustomAnnotation).orElse(null)

        expect:
        "first valuesecond value" == val
    }
}
