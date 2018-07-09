package io.micronaut.inject.parallel

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ParallelBeanSpec extends Specification {

    void "test initialize bean in parallel"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('parallel.bean.enabled':true)
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        expect:
        ctx.getBeanRegistrations(ParallelBean).isEmpty()
        conditions.eventually {
            ctx.getBeanRegistrations(ParallelBean).size() == 1
        }

        cleanup:
        ctx.close()
    }
}
