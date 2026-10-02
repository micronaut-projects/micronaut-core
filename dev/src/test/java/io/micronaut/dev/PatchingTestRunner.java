package io.micronaut.dev;

import io.micronaut.context.reload.InPlaceResourceReloader;
import io.micronaut.dev.test.Cancellation;
import io.micronaut.dev.test.TestEventListener;
import io.micronaut.dev.test.TestId;
import io.micronaut.dev.test.TestOutcome;
import io.micronaut.dev.test.TestRunRequest;
import io.micronaut.dev.test.TestRunStarted;
import io.micronaut.dev.test.TestRunSummary;
import io.micronaut.dev.test.TestRunner;
import io.micronaut.dev.test.TestStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A runner that keeps state built over the loader of its last run, as an interpreter would: it reads a message from
 * the loader on each run, and patches a changed text resource into what it keeps when asked.
 */
public final class PatchingTestRunner implements TestRunner {

    static final String ID = "patching";
    static final String MESSAGE = "app/message.txt";
    static final List<ClassLoader> RUN_LOADERS = new CopyOnWriteArrayList<>();
    static final List<String> RUN_MESSAGES = new CopyOnWriteArrayList<>();
    static final List<Set<String>> PATCHES = new CopyOnWriteArrayList<>();
    static volatile boolean refuse;
    static volatile boolean fail;

    private volatile ClassLoader kept;
    private volatile String keptMessage;

    static void reset() {
        RUN_LOADERS.clear();
        RUN_MESSAGES.clear();
        PATCHES.clear();
        refuse = false;
        fail = false;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public TestRunSummary run(TestRunRequest request, TestEventListener listener, Cancellation cancellation) {
        ClassLoader loader = request.classLoader();
        if (loader != kept) {
            // a loader not seen before: what the runner keeps is built again over it
            kept = loader;
            keptMessage = read(loader);
        }
        RUN_LOADERS.add(loader);
        RUN_MESSAGES.add(keptMessage);
        listener.runStarted(new TestRunStarted(request.runId(), ID, request.selection(), Instant.now()));
        TestId test = new TestId(request.runId() + "/message", "app.MessageTest", "message", "message");
        listener.testStarted(test);
        listener.testFinished(test, new TestOutcome(TestStatus.PASSED, Duration.ZERO, null, null));
        TestRunSummary summary = new TestRunSummary(request.runId(), 1, 0, 0, 0, Duration.ZERO, false, true);
        listener.runFinished(summary);
        return summary;
    }

    @Override
    public Optional<InPlaceResourceReloader> inPlaceReloader(ClassLoader classLoader) {
        if (classLoader != kept) {
            return Optional.empty();
        }
        return Optional.of(new InPlaceResourceReloader() {
            @Override
            public boolean canReload(Set<String> changedResources, Set<String> removedResources) {
                return !refuse && removedResources.isEmpty() && changedResources.stream().allMatch(name -> name.endsWith(".txt"));
            }

            @Override
            public Result reload(Set<String> changedResources) {
                if (fail) {
                    throw new IllegalStateException("the patch failed");
                }
                PATCHES.add(Set.copyOf(changedResources));
                // the new contents are readable through the generation
                keptMessage = read(classLoader);
                return new Result(changedResources.size(), "message(s)");
            }
        });
    }

    private static String read(ClassLoader loader) {
        try (InputStream in = loader.getResourceAsStream(MESSAGE)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
