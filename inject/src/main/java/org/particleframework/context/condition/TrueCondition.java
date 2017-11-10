package org.particleframework.context.condition;

/**
 * A {@link Condition} that simply returns true
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class TrueCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        return true;
    }
}
