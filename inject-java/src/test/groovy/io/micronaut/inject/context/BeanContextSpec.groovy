package io.micronaut.inject.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.core.convert.TypeConverter
import spock.lang.Specification

class BeanContextSpec extends Specification {

    void "test find bean definition with multiple candidates"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.getBeansOfType(TypeConverter).size() > 1
        // should not be present as there is no concrete candidate
        !context.findBeanDefinition(TypeConverter).isPresent()

        cleanup:
        context?.close()
    }
}
