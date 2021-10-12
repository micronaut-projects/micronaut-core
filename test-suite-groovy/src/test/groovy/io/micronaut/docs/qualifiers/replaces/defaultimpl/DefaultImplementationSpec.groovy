package io.micronaut.docs.qualifiers.replaces.defaultimpl

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class DefaultImplementationSpec extends Specification {

    void "test the default is replaced"() {
        given:
        BeanContext ctx = BeanContext.run()

        when:
        ResponseStrategy responseStrategy = ctx.getBean(ResponseStrategy)

        then:
        responseStrategy instanceof CustomResponseStrategy

        cleanup:
        ctx.close()
    }
}
