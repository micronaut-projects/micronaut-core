@Configuration
@Requires(condition = { ConditionContext context -> System.getenv('TRAVIS') })
package org.particleframework.inject.configurations.requirescondition

import org.particleframework.context.annotation.Configuration
import org.particleframework.context.annotation.Requires
import org.particleframework.context.condition.ConditionContext
import org.particleframework.inject.configurations.NotABean

