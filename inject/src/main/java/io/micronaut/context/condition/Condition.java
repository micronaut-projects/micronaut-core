package io.micronaut.context.condition;

import java.util.function.Predicate;

/**
 * A condition allows conditional loading of a {@link io.micronaut.inject.BeanConfiguration}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@FunctionalInterface
public interface Condition extends Predicate<ConditionContext> {

    /**
     * Check whether a specific condition is met
     *
     * @param context The condition context
     * @return True if has been met
     */
    boolean matches(ConditionContext context);

    @Override
    default boolean test(ConditionContext condition) {
        return matches(condition);
    }
}
