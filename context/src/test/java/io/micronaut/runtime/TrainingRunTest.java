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
package io.micronaut.runtime;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the training run switch ({@link ApplicationConfiguration#TRAINING_ENABLED}) of
 * {@link Micronaut#start()}: the exit itself runs in a fresh JVM.
 */
class TrainingRunTest {
    static final String SPEC_NAME = "TrainingRunTest";
    // Tests that start a child JVM with the test class path: a constrained run can exclude this tag
    private static final String CHILD_JVM = "child-jvm";
    private static final String SERVER = "training-run-test.server";
    private static final String TRAINING_LOG = "Training run (" + ApplicationConfiguration.TRAINING_ENABLED + "=true)";
    private static final String ANNOUNCEMENT = TRAINING_LOG + ": this JVM is a training run and does not serve traffic.";
    private static final String DEPLOYMENT_WARNING = "Never set this property or MICRONAUT_APPLICATION_TRAINING_ENABLED on a deployment target";
    private static final String FAREWELL = "This JVM was a training run and served no traffic";

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void reset() {
        TestApplication.STARTED.set(0);
        TestApplication.STOPPED.set(0);
        logs.start();
        micronautLogger().addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        micronautLogger().detachAppender(logs);
        logs.stop();
    }

    private static ch.qos.logback.classic.Logger micronautLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Micronaut.class);
    }

    private List<String> warnings() {
        return logs.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void stopsTheApplicationWithoutExitingUnderTheTestEnvironment() {
        // A server application: without the switch, start() waits for a shutdown
        ApplicationContext context = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .properties(Map.<String, Object>of("spec.name", SPEC_NAME, ApplicationConfiguration.TRAINING_ENABLED, "true"))
            .start();

        assertFalse(context.isRunning());
        assertEquals(1, TestApplication.STARTED.get());
        // Closing the context stops the application bean a second time, as the shutdown hook does
        assertTrue(TestApplication.STOPPED.get() >= 1);
        // One warning when the switch is read, one on the way out, and neither announces an exit here
        assertEquals(List.of(
            ANNOUNCEMENT + " It stops the application as soon as startup has completed. " + DEPLOYMENT_WARNING,
            TRAINING_LOG + ": startup completed, stopping the application. " + FAREWELL), warnings());
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void trueInAnyCaseEnablesTheSwitch() {
        // A boolean is what a YAML or TOML file yields for "enabled: true"
        for (Object value : List.<Object>of("TRUE", "True", true)) {
            ApplicationContext context = Micronaut.build(new String[0])
                .environments(Environment.TEST)
                .properties(Map.of("spec.name", SPEC_NAME, ApplicationConfiguration.TRAINING_ENABLED, value))
                .start();

            assertFalse(context.isRunning(), value::toString);
        }
    }

    @Test
    void otherTruthyValuesLeaveTheSwitchOff() {
        // A Boolean conversion would accept these, but @Requires(pattern = "(?i)true") on the training beans would not
        for (String value : List.of("yes", "on", "y", "1", "false")) {
            try (ApplicationContext context = Micronaut.build(new String[0])
                .environments(Environment.TEST)
                .properties(Map.<String, Object>of("spec.name", SPEC_NAME, SERVER, "false", ApplicationConfiguration.TRAINING_ENABLED, value))
                .start()) {

                assertTrue(context.isRunning(), value);
            }
        }
        assertEquals(List.of(), warnings());
    }

    @Test
    void warnsThatTheSwitchHasNoEffectWithoutAnEmbeddedApplication() {
        try (ApplicationContext context = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .properties(Map.<String, Object>of(ApplicationConfiguration.TRAINING_ENABLED, "true"))
            .start()) {

            assertTrue(context.isRunning());
            assertFalse(context.containsBean(EmbeddedApplication.class));
            assertEquals(List.of(TRAINING_LOG + ": the switch has no effect, because there is no EmbeddedApplication to stop. "
                + "The JVM exits when the application's own threads end"), warnings());
        }
    }

    @Test
    void leavesTheApplicationRunningWhenTheSwitchIsOff() {
        // Not a server application, so start() returns without the switch
        try (ApplicationContext context = Micronaut.build(new String[0])
            .environments(Environment.TEST)
            .properties(Map.<String, Object>of("spec.name", SPEC_NAME, SERVER, "false"))
            .start()) {

            assertTrue(context.isRunning());
            assertEquals(1, TestApplication.STARTED.get());
            assertEquals(0, TestApplication.STOPPED.get());
            assertEquals(List.of(), warnings());
        }
    }

    @Test
    @Tag(CHILD_JVM)
    void exitsWithZeroOnceStarted() {
        ChildJvm child = ChildJvm.run(Map.of(), "-D" + ApplicationConfiguration.TRAINING_ENABLED + "=true");

        assertEquals(0, child.exitCode(), child::output);
        // The switch is announced before the application starts, and again on the way out
        int announced = child.output().indexOf(ANNOUNCEMENT + " It stops the application as soon as startup has completed and exits with status 0. " + DEPLOYMENT_WARNING);
        int started = child.output().indexOf(TestApplication.STARTED_MESSAGE);
        int leaving = child.output().indexOf(TRAINING_LOG + ": startup completed, stopping the application and exiting with status 0. " + FAREWELL);
        int stopped = child.output().indexOf(TestApplication.STOPPED_MESSAGE);
        assertTrue(announced >= 0 && announced < started && started < leaving && leaving < stopped, child::output);
    }

    @Test
    @Tag(CHILD_JVM)
    void environmentVariableEnablesTheSwitch() {
        // Not the canonical "true": the switch ignores the case
        ChildJvm child = ChildJvm.run(Map.of("MICRONAUT_APPLICATION_TRAINING_ENABLED", "TRUE"));

        assertEquals(0, child.exitCode(), child::output);
        assertTrue(child.output().contains(ANNOUNCEMENT), child::output);
        assertTrue(child.output().contains(FAREWELL), child::output);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static final class TestApplication implements EmbeddedApplication<TestApplication> {
        static final String STARTED_MESSAGE = "TestApplication started";
        static final String STOPPED_MESSAGE = "TestApplication stopped";
        static final AtomicInteger STARTED = new AtomicInteger();
        static final AtomicInteger STOPPED = new AtomicInteger();

        private final ApplicationContext applicationContext;
        private final ApplicationConfiguration applicationConfiguration;
        private final boolean server;
        private volatile boolean running;

        TestApplication(ApplicationContext applicationContext,
                        ApplicationConfiguration applicationConfiguration,
                        @Value("${" + SERVER + ":true}") boolean server) {
            this.applicationContext = applicationContext;
            this.applicationConfiguration = applicationConfiguration;
            this.server = server;
        }

        @Override
        public ApplicationContext getApplicationContext() {
            return applicationContext;
        }

        @Override
        public ApplicationConfiguration getApplicationConfiguration() {
            return applicationConfiguration;
        }

        @Override
        public boolean isServer() {
            return server;
        }

        @Override
        public boolean isShutdownHookNeeded() {
            return false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public TestApplication start() {
            running = true;
            STARTED.incrementAndGet();
            System.out.println(STARTED_MESSAGE);
            return this;
        }

        @Override
        public TestApplication stop() {
            running = false;
            STOPPED.incrementAndGet();
            System.out.println(STOPPED_MESSAGE);
            return this;
        }
    }

    /**
     * The application run by the child JVM: a server application, so without the switch it would
     * wait for a shutdown and the child would time out.
     */
    static final class Main {
        public static void main(String[] args) {
            Micronaut.build(args)
                .properties(Map.<String, Object>of("spec.name", SPEC_NAME))
                .start();
        }
    }

    private record ChildJvm(int exitCode, String output) {
        static ChildJvm run(Map<String, String> environment, String... jvmArgs) {
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            // The child runs next to the forked test JVMs: bound its heap and its GC threads
            command.addAll(List.of("-Xmx128m", "-XX:+UseSerialGC"));
            command.addAll(List.of(jvmArgs));
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(Main.class.getName());
            try {
                ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
                builder.environment().putAll(environment);
                Process process = builder.start();
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
