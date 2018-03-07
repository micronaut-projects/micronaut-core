package io.micronaut.inject.configurations.requiresconditiontrue;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;

public class TrueEnvCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        return true;
    }
}
