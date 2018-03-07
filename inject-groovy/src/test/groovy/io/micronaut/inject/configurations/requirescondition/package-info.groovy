@Configuration
@Requires(condition = { ConditionContext context -> System.getenv('TRAVIS') })
package io.micronaut.inject.configurations.requirescondition

import io.micronaut.context.annotation.Configuration
import io.micronaut.context.annotation.Requires
import io.micronaut.context.condition.ConditionContext
import io.micronaut.inject.configurations.NotABean

