package io.micronaut.inject.scope.custom.perbean;

/**
 * Runs a creation on another thread and waits a bounded time for it, which is what a {@code @PostConstruct}
 * that hands work to a pool and joins it does.
 */
final class OtherThread {

    static final long WAIT_MILLIS = 1000;

    private OtherThread() {
    }

    static boolean creates(Runnable creation) throws InterruptedException {
        Thread thread = new Thread(creation, "other-scope-creation");
        thread.setDaemon(true);
        thread.start();
        thread.join(WAIT_MILLIS);
        return !thread.isAlive();
    }
}
