package io.micronaut.inject.scope.custom.perbean;

import io.micronaut.context.BeanContext;
import jakarta.annotation.PostConstruct;

/**
 * A bean of the per-bean-locking scope whose creation waits for another thread to create another bean of the
 * same scope.
 */
@PerBeanScope
public class PerBeanWaiter {

    private final BeanContext beanContext;
    private boolean otherCreatedInTime;

    public PerBeanWaiter(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @PostConstruct
    void init() throws InterruptedException {
        otherCreatedInTime = OtherThread.creates(() -> beanContext.getBean(PerBeanOther.class));
    }

    public boolean isOtherCreatedInTime() {
        return otherCreatedInTime;
    }
}
