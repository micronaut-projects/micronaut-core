package org.particleframework.inject.configurations.requiresconditiontrue;

import org.particleframework.context.condition.Condition;
import org.particleframework.context.condition.ConditionContext;

public class TrueEnvCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        return true;
    }
}
