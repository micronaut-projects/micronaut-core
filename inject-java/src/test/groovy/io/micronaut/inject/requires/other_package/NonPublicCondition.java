package io.micronaut.inject.requires.other_package;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;

class NonPublicCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        return true;
    }
}
