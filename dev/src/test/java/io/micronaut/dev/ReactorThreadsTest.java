package io.micronaut.dev;

import io.micronaut.dev.loader.DevClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The threads of Reactor's schedulers have the parent tier's loader, whatever thread creates them, and their tasks
 * run with the current generation's: in this JVM, so that what {@link ReactorGenerationMemoryTest} checks in a JVM
 * of its own is seen step by step.
 */
class ReactorThreadsTest {

    @TempDir
    Path dir;

    private ClassLoader parentTier;
    private DevClassLoader loader;
    private ClassLoader testLoader;

    @BeforeEach
    void setUp() throws Exception {
        parentTier = ReactorThreadsTest.class.getClassLoader();
        loader = new DevClassLoader(parentTier, List.of(Files.createDirectories(dir.resolve("classes"))), dir.resolve("generations"));
        testLoader = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void tearDown() {
        Thread.currentThread().setContextClassLoader(testLoader);
        ReactorThreads.release(loader);
        Schedulers.resetFactory();
    }

    @Test
    void theThreadsOfEverySchedulerHaveTheParentTierAndTheirTasksTheCurrentGeneration() throws Exception {
        ReactorThreads.use(loader, parentTier);
        // the schedulers and their threads are created from a thread of the generation, as by an application
        Thread.currentThread().setContextClassLoader(loader.current());
        List<Scheduler> created = List.of(
            Schedulers.newBoundedElastic(2, 10, "reactor-threads-test"),
            Schedulers.newParallel("reactor-threads-test", 1),
            Schedulers.newSingle("reactor-threads-test")
        );
        try {
            for (Scheduler scheduler : List.of(Schedulers.boundedElastic(), Schedulers.parallel(), Schedulers.single(), created.get(0), created.get(1), created.get(2))) {
                AtomicReference<Thread> ran = new AtomicReference<>();
                ClassLoader during = Mono.fromCallable(() -> {
                    ran.set(Thread.currentThread());
                    return Thread.currentThread().getContextClassLoader();
                }).subscribeOn(scheduler).block();
                assertSame(loader.current(), during, "a task on " + scheduler + " runs with the current generation's loader");
                assertSame(parentTier, awaitContextClassLoader(ran.get(), parentTier), "a thread of " + scheduler + " has the parent tier's loader between tasks");
            }
            boolean evictor = false;
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.getName().startsWith("boundedElastic-evictor-")) {
                    assertNotSame(loader.current(), thread.getContextClassLoader(), thread.getName());
                    evictor |= thread.getContextClassLoader() == parentTier;
                }
            }
            assertTrue(evictor, "the evictor of boundedElastic starts with the parent tier's loader");
        } finally {
            created.forEach(Scheduler::dispose);
        }
    }

    @Test
    void aTaskRunsWithTheGenerationCurrentWhenItRuns() {
        ReactorThreads.use(loader, parentTier);
        ClassLoader first = loader.current();
        assertSame(first, Mono.fromCallable(() -> Thread.currentThread().getContextClassLoader()).subscribeOn(Schedulers.parallel()).block());
        loader.swap();
        ClassLoader second = Mono.fromCallable(() -> Thread.currentThread().getContextClassLoader()).subscribeOn(Schedulers.parallel()).block();
        assertSame(loader.current(), second);
        assertNotSame(first, second);
    }

    @Test
    void usingTheSameLoaderAgainKeepsTheSharedSchedulers() {
        ReactorThreads.use(loader, parentTier);
        Scheduler parallel = Schedulers.parallel();
        ReactorThreads.use(loader, parentTier);
        assertSame(parallel, Schedulers.parallel());
        assertFalse(parallel.isDisposed());
    }

    @Test
    void releasingRestoresReactorsOwnFactoryOnlyForTheLoaderThatSetIt() throws Exception {
        ReactorThreads.use(loader, parentTier);
        DevClassLoader other = new DevClassLoader(parentTier, List.of(dir.resolve("classes")), dir.resolve("other-generations"));
        ReactorThreads.release(other);
        assertSame(loader.current(), Mono.fromCallable(() -> Thread.currentThread().getContextClassLoader()).subscribeOn(Schedulers.parallel()).block());

        ReactorThreads.release(loader);
        Thread.currentThread().setContextClassLoader(testLoader);
        ClassLoader after = Mono.fromCallable(() -> Thread.currentThread().getContextClassLoader()).subscribeOn(Schedulers.parallel()).block();
        assertNotSame(loader.current(), after, "no hook sets the generation's loader once released");
        assertSame(testLoader, after, "Reactor's own factory: the thread has the loader of the thread that created it");
        // released already: a no-op
        ReactorThreads.release(loader);
    }

    private static ClassLoader awaitContextClassLoader(Thread thread, ClassLoader expected) throws InterruptedException {
        // the task's thread restores its loader just after the task signalled its value
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (thread.getContextClassLoader() != expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return thread.getContextClassLoader();
    }
}
