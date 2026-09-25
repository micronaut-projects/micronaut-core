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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.TrainingWarmupConfiguration;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the warm-up of a training run ({@link ApplicationConfiguration#TRAINING_ENABLED}) against
 * the Netty server: the exit itself runs in a fresh JVM.
 */
class TrainingWarmupTest {
    static final String SPEC_NAME = "TrainingWarmupTest";
    private static final String OK_PATH = "/training-warmup/ok";
    private static final String FAIL_PATH = "/training-warmup/fail";
    private static final String PATHS = TrainingWarmupConfiguration.PREFIX + ".paths";
    private static final String REPEAT = TrainingWarmupConfiguration.PREFIX + ".repeat";

    @BeforeEach
    void reset() {
        WarmupController.REQUESTS.clear();
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
    void failingWarmupRequestExitsWithNonZero() {
        ChildJvm child = ChildJvm.run(
            "-D" + ApplicationConfiguration.TRAINING_ENABLED + "=true",
            "-D" + PATHS + "=" + OK_PATH + "," + FAIL_PATH + "," + OK_PATH);

        assertEquals(1, child.exitCode(), child::output);
        assertTrue(child.output().contains(FAIL_PATH + " returned status 500"), child::output);
        // The warm-up stops at the failing request
        assertEquals(1, child.output().lines().filter(line -> line.startsWith(WarmupController.REQUEST_LOG)).count(), child::output);
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/training-warmup")
    static class WarmupController {
        static final String REQUEST_LOG = "Training warm-up request received: ";
        static final List<String> REQUESTS = new CopyOnWriteArrayList<>();

        @Get("/ok")
        String ok(HttpRequest<?> request) {
            String uri = request.getUri().toString();
            REQUESTS.add(uri);
            System.out.println(REQUEST_LOG + uri);
            return "ok";
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
