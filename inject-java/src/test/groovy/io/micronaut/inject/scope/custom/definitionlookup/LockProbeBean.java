package io.micronaut.inject.scope.custom.definitionlookup;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.TimeUnit;

/**
 * Its pre-destroy hook asks the scope a question from another thread. That thread needs the scope's read lock,
 * so it only answers in time when the hook is not running under the scope's write lock.
 */
@LookupScope
public class LockProbeBean {

    public static volatile boolean otherThreadAnswered;

    private final LookupScopeImpl scope;

    public LockProbeBean(LookupScopeImpl scope) {
        this.scope = scope;
    }

    @PreDestroy
    void destroy() throws InterruptedException {
        Thread probe = new Thread(() -> {
            scope.findBeanRegistration(new Object());
            otherThreadAnswered = true;
        });
        probe.start();
        probe.join(TimeUnit.SECONDS.toMillis(5));
    }
}
