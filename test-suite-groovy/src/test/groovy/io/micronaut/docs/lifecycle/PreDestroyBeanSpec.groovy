
package io.micronaut.docs.lifecycle

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class PreDestroyBeanSpec extends Specification {

    void "test bean closing on context close"() {
        when:
        // tag::start[]
        ApplicationContext ctx = ApplicationContext.run()
        PreDestroyBean preDestroyBean = ctx.getBean(PreDestroyBean)
        Connection connection = ctx.getBean(Connection)
        Cache cache = ctx.getBean(Cache)
        ctx.stop()
        // end::start[]

        then:
        preDestroyBean.stopped.get()
        connection.stopped.get()
        cache.flushed.get()
    }
}
