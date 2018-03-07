@Configuration
@Requires(condition = TravisEnvCondition.class)
package io.micronaut.inject.configurations.requiresconditionfalse

import io.micronaut.context.annotation.Configuration
import io.micronaut.context.annotation.Requires
