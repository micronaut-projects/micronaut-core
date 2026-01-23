
@Configuration
@Requires(property = "spec.name", value = "ConditionFindBeanWithInterfaceConfigSpec")
@Requires(condition = FindBeanCondition.class)
package io.micronaut.inject.configproperties.condition.data;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
