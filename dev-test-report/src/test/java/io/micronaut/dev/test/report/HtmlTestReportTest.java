package io.micronaut.dev.test.report;

import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.livereload.netty.NettyLiveReloadServer;
import io.micronaut.dev.test.TestFailure;
import io.micronaut.dev.test.TestId;
import io.micronaut.dev.test.TestOutcome;
import io.micronaut.dev.test.TestOutput;
import io.micronaut.dev.test.TestRunStarted;
import io.micronaut.dev.test.TestRunSummary;
import io.micronaut.dev.test.TestSelection;
import io.micronaut.dev.test.TestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlTestReportTest {

    private static final TestId ADDS = new TestId("[engine:junit-jupiter]/[class:example.CalculatorTest]/[method:adds()]", "example.CalculatorTest", "adds()", "adds()");
    private static final TestId FAILS = new TestId("[engine:junit-jupiter]/[class:example.CalculatorTest]/[method:fails()]", "example.CalculatorTest", "fails()", "fails()");
    private static final TestId OTHER = new TestId("[engine:junit-jupiter]/[class:example.OtherTest]/[method:works()]", "example.OtherTest", "works()", "works()");

    @Test
    void thePageHoldsTheLatestResultsTheLogoAndLinksFramesToTheirSources(@TempDir Path directory) throws Exception {
        Path sources = directory.resolve("src");
        Path calculator = sources.resolve("example/CalculatorTest.java");
        Files.createDirectories(calculator.getParent());
        Files.writeString(calculator, "package example;");
        Path report = directory.resolve("report");
        HtmlTestReport html = new HtmlTestReport(report, null, List.of(sources));

        run(html, TestSelection.all(), true);

        String page = Files.readString(report.resolve("index.html"));
        String state = Files.readString(report.resolve("state.json"));
        assertTrue(page.contains("aria-label=\"Micronaut\""), "the logo is inlined");
        assertFalse(page.contains("<!--logo-->"));
        assertFalse(page.contains("/*state*/null"), "the state is embedded");
        assertTrue(page.contains(state), "the page embeds the state it was written with");
        // output that would end the script element is escaped
        assertFalse(page.contains("</script><b>"));
        assertTrue(state.contains("\\u003c/script\\u003e\\u003cb\\u003e"));
        assertTrue(state.contains("\"example/CalculatorTest.java\":\"" + calculator.toAbsolutePath().normalize().toString().replace("\\", "\\\\") + "\""));
        assertTrue(state.contains("\"status\":\"failed\""));
        assertTrue(state.contains("\"message\":\"wrong sum\""));
        assertTrue(state.contains("\"history\":[{"));
        assertTrue(state.contains("\"running\":null"));
        assertTrue(state.contains("\"live\":false"));
        assertNull(html.url());
    }

    @Test
    void deletedClassesLeaveTheReportAndACompileFailureIsShownUntilItCompilesAgain(@TempDir Path directory) throws Exception {
        HtmlTestReport html = new HtmlTestReport(directory, null, List.of());
        run(html, TestSelection.all(), true);
        assertTrue(html.state().contains("example.OtherTest"));

        html.testClassesRemoved(Set.of("example.OtherTest"));
        assertFalse(html.state().contains("example.OtherTest"));
        assertFalse(Files.readString(directory.resolve("state.json")).contains("example.OtherTest"));

        html.compilationFailed(new CompileFailure(SourceKind.JAVA,
            List.of(new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "cannot find symbol", directory.resolve("Broken.java"), 3, 7)), Instant.now()));
        String failed = Files.readString(directory.resolve("state.json"));
        assertTrue(failed.contains("\"compileFailure\":{\"kind\":\"java\""));
        assertTrue(failed.contains("cannot find symbol"));
        assertTrue(failed.contains("example.CalculatorTest"), "the last results stand");

        html.compilationRecovered();
        assertTrue(Files.readString(directory.resolve("state.json")).contains("\"compileFailure\":null"));
    }

    @Test
    void theServerServesThePageAndAnOpenPageFollowsTheRunTestByTest(@TempDir Path directory) throws Exception {
        try (LiveReloadServer server = NettyLiveReloadServer.start(0)) {
            HtmlTestReport html = new HtmlTestReport(directory, server, List.of());
            assertEquals("http://localhost:" + server.port() + HtmlTestReport.PREFIX, html.url());
            HttpClient client = HttpClient.newHttpClient();
            LinkedBlockingQueue<String> events = listen(client, server);

            run(html, TestSelection.ofClasses(Set.of("example.CalculatorTest"), "1 class affected by CalculatorTest.java"), true);

            String started = events.poll(10, TimeUnit.SECONDS);
            assertNotNull(started);
            assertTrue(started.startsWith("{\"type\":\"run-started\""), started);
            assertTrue(started.contains("1 class affected"));
            String testStarted = events.poll(10, TimeUnit.SECONDS);
            assertNotNull(testStarted);
            assertTrue(testStarted.contains("\"type\":\"test-started\""), testStarted);
            String finished = next(events, "test-finished");
            assertTrue(finished.contains("\"className\":\"example.CalculatorTest\""));
            String state = next(events, "state");
            assertTrue(state.contains("\"live\":true"));

            HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + HtmlTestReport.PREFIX)).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("aria-label=\"Micronaut\""));
            HttpResponse<String> json = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + HtmlTestReport.PREFIX + "state.json")).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, json.statusCode());
            assertTrue(json.body().startsWith("{\"version\":1"));
        }
    }

    @Test
    void aSnapshotTakenDuringARunHoldsTheTestsFinishedSoFar(@TempDir Path directory) throws Exception {
        HtmlTestReport html = new HtmlTestReport(directory, null, List.of());
        html.runStarted(new TestRunStarted("run-1", "junit-platform", TestSelection.all(), Instant.now()));
        html.testStarted(ADDS);
        html.testFinished(ADDS, new TestOutcome(TestStatus.PASSED, Duration.ofMillis(3), null, null));

        assertTrue(html.state().contains("\"current\":[{\"className\":\"example.CalculatorTest\",\"test\":{\"id\":\"" + ADDS.uniqueId()));
        // the first test of a run saves the snapshot a page opening now reads
        assertTrue(Files.readString(directory.resolve("state.json")).contains("\"finished\":1"));

        html.runFinished(new TestRunSummary("run-1", 1, 0, 0, 0, Duration.ofMillis(3), false, true));
        assertTrue(html.state().contains("\"current\":[]"));
    }

    @Test
    void stringsAreEscapedForJsonInsideAPage() {
        String json = new Json().beginObject()
            .field("text", "a\"b\\c\n</script><!-- & \u2028 \u0001 \ud800")
            .name("list").beginArray().value(1).value(true).value((String) null).endArray()
            .endObject().toString();
        assertEquals("{\"text\":\"a\\\"b\\\\c\\n\\u003c/script\\u003e\\u003c!-- \\u0026 \\u2028 \\u0001 \\ufffd\",\"list\":[1,true,null]}", json);
    }

    private static void run(HtmlTestReport html, TestSelection selection, boolean complete) {
        html.runStarted(new TestRunStarted("run-1", "junit-platform", selection, Instant.now()));
        html.testStarted(ADDS);
        html.output(ADDS, TestOutput.STDOUT, "</script><b>loud</b>");
        html.testFinished(ADDS, new TestOutcome(TestStatus.PASSED, Duration.ofMillis(3), null, null));
        html.testStarted(FAILS);
        html.testFinished(FAILS, new TestOutcome(TestStatus.FAILED, Duration.ofMillis(5),
            new TestFailure("org.opentest4j.AssertionFailedError", "wrong sum",
                "org.opentest4j.AssertionFailedError: wrong sum\n\tat example.CalculatorTest.fails(CalculatorTest.java:12)\n\tat java.base/java.lang.Thread.run(Thread.java:1583)"),
            null));
        if (selection.everything()) {
            html.testStarted(OTHER);
            html.testFinished(OTHER, new TestOutcome(TestStatus.PASSED, Duration.ofMillis(1), null, null));
        }
        html.runFinished(new TestRunSummary("run-1", selection.everything() ? 2 : 1, 1, 0, 0, Duration.ofMillis(20), false, complete));
    }

    private static String next(LinkedBlockingQueue<String> events, String type) throws InterruptedException {
        while (true) {
            String event = events.poll(10, TimeUnit.SECONDS);
            assertNotNull(event, "no " + type + " event");
            if (event.startsWith("{\"type\":\"" + type + "\"")) {
                return event;
            }
        }
    }

    private static LinkedBlockingQueue<String> listen(HttpClient client, LiveReloadServer server) throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + server.port() + LiveReloadServer.EVENTS_PATH + "?topic=" + HtmlTestReport.TOPIC), new WebSocket.Listener() {
            private final StringBuilder partial = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                partial.append(data);
                if (last) {
                    received.add(partial.toString());
                    partial.setLength(0);
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }
        }).get(10, TimeUnit.SECONDS);
        // the subscription is registered once the handshake completed on the server
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (server.subscribers(HtmlTestReport.TOPIC) == 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        return received;
    }
}
