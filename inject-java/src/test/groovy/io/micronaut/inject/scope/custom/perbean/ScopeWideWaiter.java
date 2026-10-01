package io.micronaut.inject.scope.custom.perbean;

import io.micronaut.context.BeanContext;
import io.micronaut.inject.scope.custom.AnotherConcurrentScope;
import jakarta.annotation.PostConstruct;

/**
 * A bean of the scope-wide-locking scope whose creation waits for another thread to create another bean of
 * the same scope.
 */
@AnotherConcurrentScope
public class ScopeWideWaiter {

    private final BeanContext beanContext;
    private boolean otherCreatedInTime;

    public ScopeWideWaiter(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @PostConstruct
    void init() throws InterruptedException {
        otherCreatedInTime = OtherThread.creates(() -> beanContext.getBean(ScopeWideOther.class));
    }

    public boolean isOtherCreatedInTime() {
        return otherCreatedInTime;
    }
}
