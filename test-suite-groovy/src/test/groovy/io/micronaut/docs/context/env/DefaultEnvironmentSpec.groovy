package io.micronaut.docs.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification

class DefaultEnvironmentSpec extends Specification {

    // tag::disableEnvDeduction[]
    void "test disable environment deduction via builder"() {
        when:
        ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()

        then:
        !ctx.environment.activeNames.contains(Environment.TEST)

        cleanup:
        ctx.close()
    }
    // end::disableEnvDeduction[]

}
