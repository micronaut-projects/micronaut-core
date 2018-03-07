package io.micronaut.inject.configurations.requiresconditionfalse;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;

public class TravisEnvCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        return System.getenv("TRAVIS") != null;
    }
}
