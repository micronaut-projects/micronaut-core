package org.particleframework.context.condition;

import java.util.function.Predicate;

/**
 * A condition allows conditional loading of a {@link org.particleframework.inject.BeanConfiguration}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@FunctionalInterface
public interface Condition<T extends ConditionContext> extends Predicate<T> {

    /**
     * Check whether a specific condition is met
     *
     * @param context The condition context
     * @return True if has been met
     */
    boolean matches(T context);

    @Override
    default boolean test(T condition) {
        return matches(condition);
    }
}
