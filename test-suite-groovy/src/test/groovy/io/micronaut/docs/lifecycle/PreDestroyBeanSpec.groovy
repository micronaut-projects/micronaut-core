
package io.micronaut.docs.lifecycle

import io.micronaut.context.BeanContext
import spock.lang.Specification

class PreDestroyBeanSpec extends Specification {

    void "test bean closing on context close"() {
        when:
        // tag::start[]
        BeanContext ctx = BeanContext.build().start()
        PreDestroyBean preDestroyBean = ctx.getBean(PreDestroyBean)
        Connection connection = ctx.getBean(Connection)
        ctx.stop()
        // end::start[]

        then:
        preDestroyBean.stopped.get()
        connection.stopped.get()
    }
}
