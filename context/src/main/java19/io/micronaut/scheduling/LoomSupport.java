import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Adds support for Loom virtual threads.
 */
public final class LoomSupport {
    /**
     * returns an empty executor service on Java < 19
     * @param threadFactory the thread factory
     * @return an empty optional
     */
    public Optional<ExecutorService> newVirtualThreadExecutorService(ThreadFactory threadFactory) {
        return Optional.of(Executors.newThreadPerTaskExecutor(threadFactory));
    }
}
