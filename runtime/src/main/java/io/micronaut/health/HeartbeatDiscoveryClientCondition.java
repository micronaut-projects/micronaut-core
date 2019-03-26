package io.micronaut.health;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.discovery.CompositeDiscoveryClient;

/**
 * Custom condition to conditionally enable the heart beat.
 *
 * @author graemerocher
 * @since 1.1
 */
public final class HeartbeatDiscoveryClientCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        final ApplicationContext beanContext = (ApplicationContext) context.getBeanContext();
        final Boolean enabled = beanContext.getProperty(HeartbeatConfiguration.ENABLED, Boolean.class).orElse(null);

        final CompositeDiscoveryClient discoveryClient = beanContext.getBean(CompositeDiscoveryClient.class);
        if ((enabled != null && enabled) || ArrayUtils.isNotEmpty(discoveryClient.getDiscoveryClients())) {
            return true;
        } else {
            context.fail("Heartbeat not enabled since no Discovery client active");
            return false;
        }
    }
}
