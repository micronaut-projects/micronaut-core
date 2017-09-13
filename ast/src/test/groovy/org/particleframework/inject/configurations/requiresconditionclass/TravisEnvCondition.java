package org.particleframework.inject.configurations.requiresconditionclass;

import org.particleframework.context.condition.Condition;
import org.particleframework.context.condition.ConditionContext;

public class TravisEnvCondition implements Condition<ConditionContext> {
    @Override
    public boolean matches(ConditionContext context) {
        return System.getenv("TRAVIS") != null;
    }
}
