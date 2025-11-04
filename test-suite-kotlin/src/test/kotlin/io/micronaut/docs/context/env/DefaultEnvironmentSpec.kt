package io.micronaut.docs.context.env

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment

class DefaultEnvironmentSpec : StringSpec({

    // tag::disableEnvDeduction[]
    "test disable environment deduction via builder" {
        val ctx = ApplicationContext.builder().deduceEnvironment(false).start()
        ctx.environment.activeNames.shouldNotContain(Environment.TEST)
        ctx.close()
    }
    // end::disableEnvDeduction[]
})
