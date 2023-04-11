package io.micronaut.http.netty.channel;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.netty.incubator.channel.uring.IOUring;

/**
 * Checks if io-uring is available.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public class IoUringAvailabilityCondition implements Condition {

    /**
     * Checks if netty's io-uring native transport is available.
     *
     * @param context The ConditionContext.
     * @return true if the io-uring native transport is available.
     */
    @Override
    public boolean matches(ConditionContext context) {
        return IOUring.isAvailable();
    }
}
