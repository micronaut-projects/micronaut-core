package org.particleframework.context.condition;

/**
 * A condition allows conditional loading of a {@link org.particleframework.inject.BeanConfiguration}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Condition {

    /**
     * Check whether a specific condition is met
     *
     * @param context The condition context
     * @return True if has been met
     */
    boolean matches(ConditionContext context);
}
