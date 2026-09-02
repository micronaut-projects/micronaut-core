package io.micronaut.inject.scope.custom.definitionlookup;

import io.micronaut.context.LifeCycle;

/**
 * Fails to stop. A {@code @PreDestroy} failure is only logged by the context, a {@link LifeCycle#stop()} failure
 * surfaces as a {@code BeanDestructionException} from the registration's close.
 */
@LookupScope
public class FailingDestroyBean implements LifeCycle<FailingDestroyBean> {

    public static int created;

    public FailingDestroyBean() {
        created++;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public FailingDestroyBean stop() {
        throw new IllegalStateException("destroy failed on purpose");
    }
}
