package org.particleframework.inject.configurations.requiresconditionfalse;

import org.particleframework.context.condition.Condition;
import org.particleframework.context.condition.ConditionContext;

public class TravisEnvCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        return System.getenv("TRAVIS") != null;
    }
}
