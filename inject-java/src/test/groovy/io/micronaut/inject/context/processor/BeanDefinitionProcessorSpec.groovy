package io.micronaut.inject.context.processor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import spock.lang.Specification

class BeanDefinitionProcessorSpec extends Specification {

    void "test bean processors are invoked"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBean(ProcessedAnnotationProcessor).beans.size() == 1
        ctx.getBean(ProcessedAnnotationProcessor).beans.first().beanType == SomeBean

        cleanup:
        ctx.close()
    }
}
