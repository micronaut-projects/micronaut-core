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
package io.micronaut.dev.test.report;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceRoot;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.test.TestFailure;
import io.micronaut.dev.test.TestId;
import io.micronaut.dev.test.TestOutcome;
import io.micronaut.dev.test.TestOutput;
import io.micronaut.dev.test.TestReportListener;
import io.micronaut.dev.test.TestResults;
import io.micronaut.dev.test.TestRunStarted;
import io.micronaut.dev.test.TestRunSummary;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The live HTML report of test mode, for every build tool: a page of the latest result of every test, failures
 * first, with their stack traces linked to the sources, their captured output, the last runs, and a change that
 * did not compile.
 *
 * <p>The report keeps its results as the JUnit XML reports do, see {@link TestResults}, and writes
 * {@code index.html} and {@code state.json} to {@code micronaut.dev.test.html-report}, by default
 * {@code build/micronaut-dev/test-report}, whenever a run starts or finishes, a change does not compile or compiles
 * again, and test classes are deleted. The page holds its state, so it opens from the file system as it is. With
 * {@code micronaut-dev-livereload} on the classpath the LiveReload server serves the directory, at
 * {@code http://localhost:35729/tests/}, and the report publishes every event on the {@code tests} topic of its
 * event channel, so the open page follows a run test by test without reloading.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class HtmlTestReport implements TestReportListener {

    /**
     * The topic of the event channel the report publishes on.
     */
    public static final String TOPIC = "tests";

    /**
     * The path the LiveReload server serves the report at by default: {@code micronaut.dev.test.html-report-path} sets
     * another.
     */
    public static final String PREFIX = "/tests/";

    /**
     * The order of the report among the report listeners: after the ones that write files a build reads.
     */
    public static final int ORDER = 100;

    private static final Logger LOG = LoggerFactory.getLogger(HtmlTestReport.class);
    private static final String TEMPLATE = "META-INF/micronaut-dev/test-report/index.html";
    private static final String LOGO = "META-INF/micronaut-dev/test-report/micronaut-logo.svg";
    private static final String BRAND = "META-INF/micronaut-dev/test-report/brand.properties";
    private static final int HISTORY = 30;
    // the captured output kept per stream and test: the JUnit XML reports keep it all
    private static final int MAX_OUTPUT = 64 * 1024;
    // a Java stack frame: at example.Type.method(Type.java:12)
    private static final Pattern FRAME = Pattern.compile("at (?:[\\w.$]+/)*([\\w.$]+)\\.[\\w$<>]+\\(([\\w$-]+\\.(?:java|kt|groovy|kts)):\\d+\\)");

    private final TestResults results = new TestResults(MAX_OUTPUT);
    private final Deque<String> history = new ArrayDeque<>();
    private final Map<String, String> sources = new LinkedHashMap<>();
    private @Nullable Path directory;
    private @Nullable LiveReloadServer server;
    private List<Path> sourceRoots = List.of();
    private String project = "";
    private boolean configured;
    private @Nullable String url;
    private @Nullable String running;
    private int finished;
    private long savedAt;
    private @Nullable String last;
    private @Nullable CompileFailure compileFailure;

    /**
     * The report of the runtime running this process, configured from its manifest when the first event comes,
     * as the service loader creates it.
     */
    public HtmlTestReport() {
    }

    /**
     * A report written to a directory, and served by a LiveReload server when there is one.
     *
     * @param directory Where the page goes
     * @param server The LiveReload server to serve it from and publish on, or null
     * @param sourceRoots The source directories stack frames link to
     */
    public HtmlTestReport(Path directory, @Nullable LiveReloadServer server, List<Path> sourceRoots) {
        this(directory, server, sourceRoots, PREFIX);
    }

    /**
     * A report written to a directory, and served by a LiveReload server at a path when there is one.
     *
     * @param directory Where the page goes
     * @param server The LiveReload server to serve it from and publish on, or null
     * @param sourceRoots The source directories stack frames link to
     * @param path The path the server serves the page at, such as {@code /tests/}
     */
    public HtmlTestReport(Path directory, @Nullable LiveReloadServer server, List<Path> sourceRoots, String path) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.server = server;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.project = projectName(directory);
        this.configured = true;
        if (server != null) {
            this.url = server.serve(path, directory);
        }
        // the page is there before the first event, at the address the server serves
        write();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * @return Where the page goes, once known
     */
    public @Nullable Path directory() {
        return directory;
    }

    /**
     * @return The address the LiveReload server serves the page at, when it does
     */
    public @Nullable String url() {
        return url;
    }

    @Override
    public synchronized void runStarted(TestRunStarted event) {
        configure();
        results.runStarted(event);
        finished = 0;
        running = new Json().beginObject()
            .field("runId", event.runId())
            .field("runner", event.runner())
            .field("selection", event.selection().description())
            .field("everything", event.selection().everything())
            .field("startedAt", event.startedAt().toString())
            .endObject().toString();
        publish(new Json().beginObject().field("type", "run-started").name("run").raw(running).endObject().toString());
        write();
    }

    @Override
    public synchronized void testStarted(TestId test) {
        results.testStarted(test);
        publish(new Json().beginObject().field("type", "test-started")
            .field("id", test.uniqueId())
            .field("className", test.className())
            .field("displayName", test.displayName())
            .endObject().toString());
    }

    @Override
    public synchronized void output(TestId test, TestOutput stream, String text) {
        results.output(test, stream, text);
    }

    @Override
    public synchronized void testFinished(TestId test, TestOutcome outcome) {
        TestResults.Result result = results.testFinished(test, outcome);
        finished++;
        // a page that opens during the run reads the tests finished so far from state.json
        long now = System.nanoTime();
        if (now - savedAt > 1_000_000_000L) {
            savedAt = now;
            saveState(state());
        }
        LiveReloadServer current = server;
        if (current != null && current.subscribers(TOPIC) > 0) {
            Json json = new Json().beginObject().field("type", "test-finished").field("className", test.className()).name("test");
            result(json, result);
            json.name("sources");
            sources(json, List.of(result));
            current.publish(TOPIC, json.endObject().toString());
        }
    }

    @Override
    public synchronized void runFinished(TestRunSummary summary) {
        results.runFinished(summary);
        Json json = new Json().beginObject()
            .field("runId", summary.runId())
            .field("passed", summary.passed())
            .field("failed", summary.failed())
            .field("errored", summary.errored())
            .field("skipped", summary.skipped())
            .field("durationMillis", summary.duration().toMillis())
            .field("cancelled", summary.cancelled())
            .field("complete", summary.complete())
            .field("selection", results.selection().description())
            .field("startedAt", results.startedAt().toString())
            .endObject();
        last = json.toString();
        history.addFirst(last);
        while (history.size() > HISTORY) {
            history.removeLast();
        }
        running = null;
        update();
    }

    @Override
    public synchronized void compilationFailed(CompileFailure failure) {
        configure();
        compileFailure = failure;
        update();
    }

    @Override
    public synchronized void compilationRecovered() {
        configure();
        compileFailure = null;
        update();
    }

    @Override
    public synchronized void testClassesRemoved(Set<String> classNames) {
        configure();
        for (String className : classNames) {
            results.remove(className);
        }
        update();
    }

    /**
     * The state the page renders, as JSON.
     *
     * @return The state
     */
    public synchronized String state() {
        Map<String, List<TestResults.Result>> all = results.all();
        Json json = new Json().beginObject()
            .field("version", 1)
            .field("project", project)
            .field("generatedAt", Instant.now().toString())
            .field("live", url != null);
        json.name("running");
        rawOrNull(json, running);
        json.field("finished", finished);
        json.name("current").beginArray();
        if (running != null) {
            results.inProgress().forEach((className, tests) -> {
                for (TestResults.Result result : tests) {
                    json.beginObject().field("className", className).name("test");
                    result(json, result);
                    json.endObject();
                }
            });
        }
        json.endArray();
        json.name("last");
        rawOrNull(json, last);
        json.name("history").beginArray();
        history.forEach(json::raw);
        json.endArray();
        json.name("compileFailure");
        CompileFailure failure = compileFailure;
        if (failure == null) {
            json.raw("null");
        } else {
            json.beginObject()
                .field("kind", failure.kind().name().toLowerCase(java.util.Locale.ROOT))
                .field("at", failure.at().toString())
                .name("diagnostics").beginArray();
            for (CompileDiagnostic diagnostic : failure.diagnostics()) {
                Path file = diagnostic.file();
                json.beginObject()
                    .field("severity", diagnostic.severity().name().toLowerCase(java.util.Locale.ROOT))
                    .field("message", diagnostic.message().strip())
                    .field("file", file == null ? null : file.toAbsolutePath().normalize().toString())
                    .field("line", diagnostic.line())
                    .field("column", diagnostic.column())
                    .endObject();
            }
            json.endArray().endObject();
        }
        json.name("classes").beginArray();
        List<TestResults.Result> every = new ArrayList<>();
        all.forEach((className, tests) -> {
            json.beginObject().field("name", className).name("tests").beginArray();
            for (TestResults.Result result : tests) {
                result(json, result);
            }
            json.endArray().endObject();
            every.addAll(tests);
        });
        json.endArray();
        json.name("sources");
        sources(json, every);
        return json.endObject().toString();
    }

    private void update() {
        String state = state();
        publish(new Json().beginObject().field("type", "state").name("state").raw(state).endObject().toString());
        write(state);
    }

    private void publish(String json) {
        LiveReloadServer current = server;
        if (current != null) {
            current.publish(TOPIC, json);
        }
    }

    private void write() {
        write(state());
    }

    private void saveState(String state) {
        Path target = directory;
        if (target == null) {
            return;
        }
        try {
            Files.createDirectories(target);
            replace(target.resolve("state.json"), state);
        } catch (IOException | UncheckedIOException e) {
            LOG.error("Cannot write the HTML test report to {}: {}", target, e.getMessage(), e);
        }
    }

    private void write(String state) {
        Path target = directory;
        if (target == null) {
            return;
        }
        try {
            Files.createDirectories(target);
            replace(target.resolve("state.json"), state);
            String page = resource(TEMPLATE)
                .replace("<!--logo-->", logo(HtmlTestReport.class.getClassLoader()))
                .replace("/*state*/null", state);
            replace(target.resolve("index.html"), page);
        } catch (IOException | UncheckedIOException e) {
            LOG.error("Cannot write the HTML test report to {}: {}", target, e.getMessage(), e);
        }
    }

    /**
     * Finds the directory and the LiveReload server of the runtime running this process, the first time an event
     * comes: the service loader creates the report before the runtime starts its server.
     */
    private void configure() {
        if (configured) {
            return;
        }
        configured = true;
        DevRuntime runtime = DevRuntime.current();
        if (runtime == null) {
            LOG.debug("No development runtime runs this process: the HTML test report is off");
            return;
        }
        DevManifest manifest = runtime.manifest();
        directory = manifest.testSettings().htmlReport();
        project = projectName(manifest.projectDir());
        List<Path> roots = new ArrayList<>();
        for (SourceRoot root : manifest.testSourceRoots()) {
            roots.add(root.path());
        }
        for (SourceRoot root : manifest.sourceRoots()) {
            roots.add(root.path());
        }
        sourceRoots = List.copyOf(roots);
        LiveReloadServer liveReload = runtime.liveReload().orElse(null);
        if (liveReload != null && !manifest.testSettings().once()) {
            server = liveReload;
            url = liveReload.serve(manifest.testSettings().htmlReportPath(), directory);
            LOG.info("Test report: {}", url);
        } else {
            LOG.info("Test report: {}", directory.resolve("index.html").toUri());
        }
        write();
    }

    private void result(Json json, TestResults.Result result) {
        TestId test = result.test();
        TestOutcome outcome = result.outcome();
        TestFailure failure = outcome.failure();
        json.beginObject()
            .field("id", test.uniqueId())
            .field("name", test.name())
            .field("displayName", test.displayName())
            .field("status", outcome.status().name().toLowerCase(java.util.Locale.ROOT))
            .field("durationMillis", outcome.duration().toMillis())
            .field("skipReason", outcome.skipReason())
            .field("type", failure == null ? null : failure.type())
            .field("message", failure == null ? null : failure.message())
            .field("stackTrace", failure == null ? null : failure.stackTrace())
            .field("out", result.out())
            .field("err", result.err())
            .endObject();
    }

    /**
     * The source files the stack frames of these results name, by the path of the file in its package, such as
     * {@code example/Type.java}, so the page links a frame to its source.
     */
    private void sources(Json json, List<TestResults.Result> of) {
        json.beginObject();
        Map<String, String> found = new LinkedHashMap<>();
        for (TestResults.Result result : of) {
            TestFailure failure = result.outcome().failure();
            if (failure == null) {
                continue;
            }
            Matcher frame = FRAME.matcher(failure.stackTrace());
            while (frame.find()) {
                String className = frame.group(1);
                int dot = className.lastIndexOf('.');
                String relative = (dot < 0 ? "" : className.substring(0, dot).replace('.', '/') + "/") + frame.group(2);
                String source = sources.computeIfAbsent(relative, this::locate);
                if (!source.isEmpty()) {
                    found.put(relative, source);
                }
            }
        }
        found.forEach(json::field);
        json.endObject();
    }

    private String locate(String relative) {
        for (Path root : sourceRoots) {
            Path file = root.resolve(relative);
            if (Files.isRegularFile(file)) {
                return file.toAbsolutePath().normalize().toString();
            }
        }
        // remembered as not found: a class of a library, or of the JDK
        return "";
    }

    private static void rawOrNull(Json json, @Nullable String raw) {
        json.raw(raw == null ? "null" : raw);
    }

    private static String projectName(Path directory) {
        Path name = directory.toAbsolutePath().normalize().getFileName();
        return name == null ? "" : name.toString();
    }

    /**
     * The logo in the page's header: the Micronaut logo, or the one a product built on Micronaut names in a
     * {@code META-INF/micronaut-dev/test-report/brand.properties} of the launch classpath, with {@code name}, the
     * product's name, and {@code logo}, the resource of an SVG, which is inlined, or a PNG, which is embedded.
     *
     * @param loader The loader to find the brand and the logo with
     * @return The logo's HTML
     */
    static String logo(ClassLoader loader) {
        Properties brand = new Properties();
        try (InputStream in = loader.getResourceAsStream(BRAND)) {
            if (in != null) {
                brand.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Cannot read the test report's brand {}: {}", BRAND, e.getMessage());
        }
        String logo = brand.getProperty("logo", "").strip();
        String name = brand.getProperty("name", "").strip();
        if (logo.isEmpty()) {
            return resource(LOGO).strip();
        }
        try (InputStream in = loader.getResourceAsStream(logo)) {
            if (in == null) {
                LOG.warn("The test report's logo {} is not on the classpath", logo);
                return resource(LOGO).strip();
            }
            byte[] bytes = in.readAllBytes();
            if (logo.endsWith(".svg")) {
                return new String(bytes, StandardCharsets.UTF_8).replaceFirst("^\\s*<\\?xml[^>]*\\?>", "").strip();
            }
            String type = logo.endsWith(".png") ? "image/png" : logo.endsWith(".webp") ? "image/webp" : "image/jpeg";
            return "<img src=\"data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes) + "\" alt=\""
                + escape(name.isEmpty() ? "Logo" : name) + "\">";
        } catch (IOException e) {
            LOG.warn("Cannot read the test report's logo {}: {}", logo, e.getMessage());
            return resource(LOGO).strip();
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String resource(String name) {
        try (InputStream in = HtmlTestReport.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("Missing resource " + name));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void replace(Path file, String content) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
