/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.compiler;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Collects the {@link CompilationProfile} of one compilation: phases are timed inclusively from
 * {@link #phase(String)} to the close of the span it returns, counters accumulate, and the output
 * directory is inventoried once the compilation wrote it. Nothing here runs unless profiling was
 * enabled on the compiler; a null profiler is a no-op at every call site through the static
 * {@link #span(CompilationProfiler, String)} and {@link #increment(CompilationProfiler, String, long)}.
 *
 * <p>The profiler is used from one thread at a time (the compiler runs javac on the calling
 * thread), but the methods synchronise anyway so a callback from another thread cannot corrupt it.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class CompilationProfiler {

    private static final Span NOOP = () -> { };

    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final Map<String, PhaseTotal> phases = new LinkedHashMap<>();
    private final Map<String, Long> counters = new TreeMap<>();
    private final List<CompilationProfile.Artifact> artifacts = new ArrayList<>();
    private final Deque<String> running = new ArrayDeque<>();
    private final long started = System.nanoTime();

    /**
     * Creates a profiler recording the JVM, its process and its uptime, which tells a compilation in
     * a fresh worker from one in a reused, warmed-up worker.
     */
    public CompilationProfiler() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        attributes.put("started", Instant.now().toString());
        attributes.put("jvm", System.getProperty("java.vm.name") + " " + System.getProperty("java.runtime.version"));
        attributes.put("jvm.pid", Long.toString(runtime.getPid()));
        attributes.put("jvm.uptime.ms", Long.toString(runtime.getUptime()));
        attributes.put("jvm.max-heap.mb", Long.toString(Runtime.getRuntime().maxMemory() / (1024 * 1024)));
    }

    /**
     * Starts a phase on the given profiler, or nothing when it is null.
     *
     * @param profiler The profiler, or null when profiling is off
     * @param name The phase name
     * @return The span to close when the phase ends
     */
    public static Span span(@Nullable CompilationProfiler profiler, String name) {
        return profiler == null ? NOOP : profiler.phase(name);
    }

    /**
     * Adds to a counter of the given profiler, or nothing when it is null.
     *
     * @param profiler The profiler, or null when profiling is off
     * @param name The counter name
     * @param delta The amount to add
     */
    public static void increment(@Nullable CompilationProfiler profiler, String name, long delta) {
        if (profiler != null) {
            profiler.count(name, delta);
        }
    }

    /**
     * Records an attribute of the compilation.
     *
     * @param name The name
     * @param value The value
     */
    public synchronized void attribute(String name, String value) {
        attributes.put(name, value);
    }

    /**
     * Starts a phase. A phase started again accumulates; a phase started while another runs is
     * nested in it for the report.
     *
     * @param name The phase name
     * @return The span to close when the phase ends
     */
    public synchronized Span phase(String name) {
        PhaseTotal total = phases.computeIfAbsent(name, n -> new PhaseTotal(running.size()));
        running.push(name);
        long start = System.nanoTime();
        return () -> {
            long elapsed = System.nanoTime() - start;
            synchronized (this) {
                total.nanos += elapsed;
                total.invocations++;
                running.remove(name);
            }
        };
    }

    /**
     * Adds to a counter.
     *
     * @param name The counter name
     * @param delta The amount to add
     */
    public synchronized void count(String name, long delta) {
        counters.merge(name, delta, Long::sum);
    }

    /**
     * Inventories the output directory: the generated Java sources, class files, Python sources and
     * bytecode, and the other resources, with their sizes.
     *
     * @param outputDirectory The output directory of the compilation
     */
    public synchronized void inventory(Path outputDirectory) {
        Map<String, long[]> totals = new TreeMap<>();
        if (Files.isDirectory(outputDirectory)) {
            try (Stream<Path> files = Files.walk(outputDirectory)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    long[] total = totals.computeIfAbsent(kind(file), k -> new long[2]);
                    total[0]++;
                    try {
                        total[1] += Files.size(file);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        artifacts.clear();
        totals.forEach((kind, total) -> artifacts.add(new CompilationProfile.Artifact(kind, total[0], total[1])));
    }

    /**
     * Ends the profile.
     *
     * @return The profile
     */
    public synchronized CompilationProfile finish() {
        attributes.put("total.ms", String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1_000_000.0));
        List<CompilationProfile.Phase> phaseList = new ArrayList<>(phases.size());
        phases.forEach((name, total) -> phaseList.add(new CompilationProfile.Phase(name, total.depth, total.nanos, total.invocations)));
        return new CompilationProfile(
            Collections.unmodifiableMap(new LinkedHashMap<>(attributes)),
            List.copyOf(phaseList),
            Collections.unmodifiableMap(new TreeMap<>(counters)),
            List.copyOf(artifacts)
        );
    }

    private static String kind(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".java")) {
            return "java-source";
        }
        if (name.endsWith(".class")) {
            return "class";
        }
        if (name.endsWith(".py")) {
            return "python-source";
        }
        if (name.endsWith(".pyc")) {
            return "python-bytecode";
        }
        return "resource";
    }

    /**
     * A running phase, closed when the phase ends.
     */
    public interface Span extends AutoCloseable {
        @Override
        void close();
    }

    private static final class PhaseTotal {
        private final int depth;
        private long nanos;
        private int invocations;

        private PhaseTotal(int depth) {
            this.depth = depth;
        }
    }
}
