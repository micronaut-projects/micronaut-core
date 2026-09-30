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
package io.micronaut.http.server.netty;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.TrainingWarmupConfiguration;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the warm-up of a training run ({@link ApplicationConfiguration#TRAINING_ENABLED}) against
 * the Netty server: the exit itself runs in a fresh JVM.
 */
class TrainingWarmupTest {
    static final String SPEC_NAME = "TrainingWarmupTest";
    // Tests that start a child JVM with the test class path: a constrained run can exclude this tag
    private static final String CHILD_JVM = "child-jvm";
    private static final String OK_PATH = "/training-warmup/ok";
    private static final String REJECTED_PATH = "/training-warmup/rejected";
    private static final String FAIL_PATH = "/training-warmup/fail";
    private static final String PATHS = TrainingWarmupConfiguration.PREFIX + ".paths";
    private static final String REPEAT = TrainingWarmupConfiguration.PREFIX + ".repeat";
    private static final String WARMUP_LOGGER = "io.micronaut.http.server.TrainingWarmup";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void reset() {
        WarmupController.REQUESTS.clear();
        WarmupController.HOSTS.clear();
        logs.start();
        warmupLogger().addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        warmupLogger().detachAppender(logs);
        logs.stop();
    }

    private static ch.qos.logback.classic.Logger warmupLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(WARMUP_LOGGER);
    }

    private List<String> logged(Level level) {
        return logs.list.stream()
            .filter(event -> event.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void warmsUpTheServerThenStopsItUnderTheTestEnvironment() {
        ApplicationContext context = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .banner(false)
            .properties(Map.of(
                "spec.name", SPEC_NAME,
                ApplicationConfiguration.TRAINING_ENABLED, "true",
                PATHS, List.of(OK_PATH, "training-warmup/ok?page=2"),
                REPEAT, 2))
            .start();

        assertFalse(context.isRunning());
        assertEquals(List.of(OK_PATH, OK_PATH + "?page=2", OK_PATH, OK_PATH + "?page=2"), WarmupController.REQUESTS);
        // The warm-up reports its own duration: the startup time Micronaut logs includes it
        assertEquals(1, logged(Level.INFO).size(), () -> logged(Level.INFO).toString());
        assertTrue(logged(Level.INFO).get(0).matches("Training warm-up sent 4 GET requests to http://\\S+:\\d+ in \\d+ms"), () -> logged(Level.INFO).toString());
        assertEquals(List.of(), logged(Level.WARN));
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void trueInAnyCaseCreatesTheWarmup() {
        // A boolean is what a YAML or TOML file yields for "enabled: true"
        for (Object value : List.<Object>of("TRUE", "True", true)) {
            WarmupController.REQUESTS.clear();
            ApplicationContext context = Micronaut.build(new String[0])
                .environments(Environment.TEST)
                .banner(false)
                .properties(Map.of(
                    "spec.name", SPEC_NAME,
                    ApplicationConfiguration.TRAINING_ENABLED, value,
                    PATHS, List.of(OK_PATH)))
                .start();

            assertFalse(context.isRunning(), value::toString);
            assertEquals(List.of(OK_PATH), WarmupController.REQUESTS, value::toString);
        }
    }

    @Test
    void otherTruthyValuesDoNotCreateTheWarmup() {
        // Micronaut.start() does not stop the application for these values either
        for (String value : List.of("yes", "on")) {
            try (ApplicationContext context = ApplicationContext.run(Map.of(
                "spec.name", SPEC_NAME,
                ApplicationConfiguration.TRAINING_ENABLED, value,
                PATHS, List.of(OK_PATH)), Environment.TEST)) {
                context.getBean(EmbeddedServer.class).start();

                assertFalse(context.containsBean(TrainingWarmupConfiguration.class), value);
                assertEquals(List.of(), WarmupController.REQUESTS, value);
            }
        }
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void rejectedRequestIsLoggedAndTheWarmupContinues() {
        ApplicationContext context = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .banner(false)
            .properties(Map.of(
                "spec.name", SPEC_NAME,
                ApplicationConfiguration.TRAINING_ENABLED, "true",
                PATHS, List.of(OK_PATH, REJECTED_PATH, "/training-warmup/missing", OK_PATH)))
            .start();

        assertFalse(context.isRunning());
        assertEquals(List.of(OK_PATH, REJECTED_PATH, OK_PATH), WarmupController.REQUESTS);
        List<String> warnings = logged(Level.WARN);
        assertEquals(2, warnings.size(), warnings::toString);
        assertTrue(warnings.get(0).matches("Training warm-up request GET http://\\S+" + REJECTED_PATH + " returned status 403: .*"), warnings::toString);
        assertTrue(warnings.get(1).matches("Training warm-up request GET http://\\S+/training-warmup/missing returned status 404: .*"), warnings::toString);
    }

    @Test
    void requestsGoToTheConfiguredHost() {
        assertEquals("localhost", warmupRequestHost(Map.of("micronaut.server.host", "localhost")));
    }

    @Test
    void requestsGoToLoopbackWhenNoHostIsSet() {
        // Not the $HOSTNAME that EmbeddedServer.getHost() falls back to
        assertEquals(loopback(), warmupRequestHost(Map.of()));
    }

    @Test
    void requestsGoToLoopbackWhenTheHostIsTheWildcardAddress() {
        assertEquals(loopback(), warmupRequestHost(Map.of("micronaut.server.host", "0.0.0.0")));
    }

    /**
     * @return The host of the {@code Host} header the warm-up request carried
     */
    private static String warmupRequestHost(Map<String, Object> serverProperties) {
        Map<String, Object> properties = new HashMap<>(serverProperties);
        properties.put("spec.name", SPEC_NAME);
        properties.put(ApplicationConfiguration.TRAINING_ENABLED, "true");
        properties.put(PATHS, List.of(OK_PATH));
        try (ApplicationContext context = ApplicationContext.run(properties, Environment.TEST)) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();

            assertEquals(1, WarmupController.HOSTS.size(), WarmupController.HOSTS::toString);
            String host = WarmupController.HOSTS.get(0);
            String port = ":" + server.getPort();
            assertTrue(host.endsWith(port), host);
            return host.substring(0, host.length() - port.length());
        }
    }

    private static String loopback() {
        String address = InetAddress.getLoopbackAddress().getHostAddress();
        return address.indexOf(':') >= 0 ? '[' + address + ']' : address;
    }

    @Test
    void repeatBelowOneIsRejected() {
        // The server is not started: NettyHttpServer stays bound when a startup listener fails
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            ApplicationConfiguration.TRAINING_ENABLED, "true",
            PATHS, List.of(OK_PATH),
            REPEAT, 0), Environment.TEST)) {

            BeanInstantiationException e = assertThrows(BeanInstantiationException.class, () -> context.getBean(TrainingWarmupConfiguration.class));

            assertTrue(e.getMessage().contains(REPEAT + " must be at least 1 but was 0"), e::getMessage);
        }
    }

    @Test
    void doesNotWarmUpAnotherServer() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            ApplicationConfiguration.TRAINING_ENABLED, "true",
            PATHS, List.of(OK_PATH)), Environment.TEST)) {
            NettyEmbeddedServer secondary = context.getBean(NettyEmbeddedServerFactory.class)
                .build(new NettyHttpServerConfiguration(context.getBean(ApplicationConfiguration.class)));
            try {
                secondary.start();
            } finally {
                secondary.stop();
            }

            assertEquals(List.of(), WarmupController.REQUESTS);
        }
    }

    @Test
    void sendsNoRequestsWithoutPaths() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            ApplicationConfiguration.TRAINING_ENABLED, "true"), Environment.TEST)) {
            context.getBean(EmbeddedServer.class).start();

            assertEquals(List.of(), WarmupController.REQUESTS);
        }
    }

    @Test
    @Tag(CHILD_JVM)
    void warmsUpThenExitsWithZero() {
        ChildJvm child = ChildJvm.run(
            "-D" + ApplicationConfiguration.TRAINING_ENABLED + "=true",
            "-D" + PATHS + "=" + OK_PATH + "," + OK_PATH + "?page=2",
            "-D" + REPEAT + "=3");

        assertEquals(0, child.exitCode(), child::output);
        assertEquals(6, child.output().lines().filter(line -> line.startsWith(WarmupController.REQUEST_LOG)).count(), child::output);
        assertTrue(child.output().contains("stopping the application and exiting with status 0"), child::output);
    }

    @Test
    @Tag(CHILD_JVM)
    void failingWarmupRequestExitsWithNonZero() {
        ChildJvm child = ChildJvm.run(
            "-D" + ApplicationConfiguration.TRAINING_ENABLED + "=true",
            "-D" + PATHS + "=" + OK_PATH + "," + REJECTED_PATH + "," + FAIL_PATH + "," + OK_PATH);

        assertEquals(1, child.exitCode(), child::output);
        // A 4xx does not stop the warm-up, a 5xx does
        assertTrue(child.output().contains(REJECTED_PATH + " returned status 403"), child::output);
        assertTrue(child.output().contains(FAIL_PATH + " returned status 500"), child::output);
        assertEquals(2, child.output().lines().filter(line -> line.startsWith(WarmupController.REQUEST_LOG)).count(), child::output);
        assertFalse(child.output().contains("exiting with status 0"), child::output);
    }

    @Test
    @Tag(CHILD_JVM)
    void serverWithSslExitsWithNonZero() {
        ChildJvm child = ChildJvm.run(
            "-D" + ApplicationConfiguration.TRAINING_ENABLED + "=true",
            "-D" + PATHS + "=" + OK_PATH,
            "-Dmicronaut.server.ssl.enabled=true",
            "-Dmicronaut.server.ssl.build-self-signed=true",
            "-Dmicronaut.server.ssl.port=-1");

        assertEquals(1, child.exitCode(), child::output);
        assertTrue(child.output().contains("The training warm-up sends plain HTTP requests, but the server uses https."), child::output);
        assertEquals(0, child.output().lines().filter(line -> line.startsWith(WarmupController.REQUEST_LOG)).count(), child::output);
        assertFalse(child.output().contains("exiting with status 0"), child::output);
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/training-warmup")
    static class WarmupController {
        static final String REQUEST_LOG = "Training warm-up request received: ";
        static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
        static final List<String> HOSTS = new CopyOnWriteArrayList<>();

        @Get("/ok")
        String ok(HttpRequest<?> request) {
            record(request);
            return "ok";
        }

        @Get("/rejected")
        HttpResponse<String> rejected(HttpRequest<?> request) {
            record(request);
            return HttpResponse.status(HttpStatus.FORBIDDEN).body("rejected");
        }

        private static void record(HttpRequest<?> request) {
            String uri = request.getUri().toString();
            REQUESTS.add(uri);
            HOSTS.add(request.getHeaders().get(HttpHeaders.HOST));
            System.out.println(REQUEST_LOG + uri);
        }

        @Get("/fail")
        HttpResponse<String> fail() {
            return HttpResponse.serverError("failed");
        }
    }

    /**
     * The application run by the child JVM: without the switch the Netty event loop would keep the
     * child alive and it would time out.
     */
    static final class Main {
        public static void main(String[] args) {
            Micronaut.build(args)
                .banner(false)
                .properties(Map.<String, Object>of("spec.name", SPEC_NAME, "micronaut.server.port", -1))
                .start();
        }
    }

    private record ChildJvm(int exitCode, String output) {
        static ChildJvm run(String... jvmArgs) {
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            // The child runs next to the forked test JVMs: bound its heap and its GC threads
            command.addAll(List.of("-Xmx256m", "-XX:+UseSerialGC"));
            command.addAll(List.of(jvmArgs));
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(Main.class.getName());
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
                if (!process.waitFor(1, TimeUnit.MINUTES)) {
                    process.destroyForcibly().waitFor();
                    return new ChildJvm(-1, "The child JVM did not exit within a minute\n" + output.get(30, TimeUnit.SECONDS));
                }
                return new ChildJvm(process.exitValue(), output.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to run the child JVM", e);
            }
        }

        private static String readAll(InputStream in) {
            try (in) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                in.transferTo(bytes);
                return bytes.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
