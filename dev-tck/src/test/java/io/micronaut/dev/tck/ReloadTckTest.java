package io.micronaut.dev.tck;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.tck.fixtures.GreeterRegistry;
import io.micronaut.dev.tck.fixtures.RetainedClock;
import io.micronaut.dev.tck.fixtures.StaticGreeterCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadTckTest {

    @TempDir
    Path project;

    @Test
    void aWatchingRegistryFollowsTheReloadAStaticCacheDoesNotAndARetainedBeanSurvives() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            harness.source("example.Greeter", greeter("one"))
                .retain(RetainedClock.class.getName());
            RetainedClock clock = startAndCheck(harness);

            harness.source("example.Greeter", greeter("two"));
            ApplicationContext second = harness.reload();
            assertEquals(2, harness.generation());
            assertEquals("two", second.getBean(GreeterRegistry.class).current().greet());

            // the registry that watches hands out the new generation's bean
            ReloadTck.assertFollowsReload(harness, context -> context.getBean(GreeterRegistry.class).current());
            // the retained bean is the same instance
            ReloadTck.assertRetained(harness, clock);

            // the static cache still holds generation one, and keeps its loader alive
            AssertionError stale = assertThrows(AssertionError.class, () -> ReloadTck.assertFollowsReload(harness, context -> StaticGreeterCache.cached));
            assertTrue(stale.getMessage().contains("generation 1"), stale.getMessage());
            AssertionError leak = assertThrows(AssertionError.class, () -> ReloadTck.assertRetiredGenerationsCollected(harness, Duration.ofSeconds(2)));
            assertTrue(leak.getMessage().contains("still reachable"), leak.getMessage());

            // a probe of the parent tier proves nothing, and the assertion says so
            AssertionError parent = assertThrows(AssertionError.class, () -> ReloadTck.assertFollowsReload(harness, context -> context.getBean(GreeterRegistry.class)));
            assertTrue(parent.getMessage().contains("not of the reloadable tier"), parent.getMessage());

            // once the cache lets go, the retired generation is collected
            StaticGreeterCache.cached = null;
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void anEditThatChangesNoClassRestartsNothingAndAPropertyValueSurvivesTheFormat() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            harness.source("example.Greeter", greeter("one")).property("tck.path", "C:\\temp\nnext");
            ApplicationContext first = harness.start();
            assertEquals("C:\\temp\nnext", first.getEnvironment().getProperty("tck.path", String.class).orElse(null));
            // the same source again: nothing to restart for, and the watcher may well have seen the write
            harness.source("example.Greeter", greeter("one"));
            assertEquals(first, harness.reload());
            assertEquals(1, harness.generation());

            // a resource edit after the start reaches the running application without waiting on the watcher
            harness.resource("application.properties", "tck.label=beta\n");
            ApplicationContext after = harness.reload();
            assertEquals("beta", after.getEnvironment().getProperty("tck.label", String.class).orElse(null));
        }
    }

    @Test
    void aStartedDirectoryStartsFromItsSourcesAgain() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            harness.source("example.Greeter", greeter("one"));
            assertEquals("one", harness.start().getBean(GreeterRegistry.class).current().greet());
        }
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            harness.source("example.Greeter", greeter("again"));
            assertEquals("again", harness.start().getBean(GreeterRegistry.class).current().greet());
        }
    }

    @Test
    void aBrokenEditReportsItsDiagnostics() {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            harness.source("example.Greeter", greeter("one"));
            harness.start();
            harness.source("example.Greeter", "package example; public class Greeter implements io.micronaut.dev.tck.fixtures.Greeter { public String greet() { return 1; } }");
            AssertionError failure = assertThrows(AssertionError.class, harness::reload);
            assertTrue(failure.getMessage().contains("incompatible types"), failure.getMessage());
            assertEquals(1, harness.generation());
        }
    }

    /**
     * Starts in a frame of its own, so that the test holds no reference to the first context.
     */
    private static RetainedClock startAndCheck(ReloadHarness harness) {
        ApplicationContext first = harness.start();
        assertEquals(1, harness.generation());
        assertEquals("one", first.getBean(GreeterRegistry.class).current().greet());
        first.getBean(StaticGreeterCache.class);
        assertNotSame(null, StaticGreeterCache.cached);
        return first.getBean(RetainedClock.class);
    }

    static String greeter(String answer) {
        return """
            package example;

            @jakarta.inject.Singleton
            public class Greeter implements io.micronaut.dev.tck.fixtures.Greeter {
                @Override
                public String greet() {
                    return "%s";
                }
            }
            """.formatted(answer);
    }
}
