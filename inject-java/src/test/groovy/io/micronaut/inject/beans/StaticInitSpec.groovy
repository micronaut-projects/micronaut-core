package io.micronaut.inject.beans


import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class StaticInitSpec extends Specification {

    void "test static init bean lifecycle"() {
        given:
            def context = ApplicationContext.run(["spec.name": "StaticInitSpec"])
        when:
            def bd = context.getBeanDefinition(DoNotInitializeBean.class)
        then:
            noExceptionThrown()
        when:
            context.getBean(bd)
        then:
            def e = thrown(Exception)
            e.cause.cause.message == "The bean shouldn't be initialized!"
        cleanup:
            context.close()
    }
}
