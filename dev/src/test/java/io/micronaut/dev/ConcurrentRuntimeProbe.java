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
package io.micronaut.dev;

import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.test.TestRunSummary;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One of the two processes of {@link ConcurrentRuntimesTest}: a runtime of the project, in run mode or in test mode,
 * that applies each edit of {@code Greeter.java} as the watcher would and checks what its generation, or its tests, see
 * of it, until the edits are done; then that it stays idle. It prints {@code probe-error:} for what went wrong and
 * {@code probe-done: <version>} for the version it ended on.
 */
final class ConcurrentRuntimeProbe {

    private ConcurrentRuntimeProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        boolean test = "test".equals(args[1]);
        Path greeter = project.resolve("src/main/java/app/Greeter.java");
        Path done = project.resolve("done");
        String seen = Files.readString(greeter);
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(project.resolve(args[1] + ".properties")), new String[0]);
        String observed = observe(runtime, project, test);
        System.out.println("probe-ready: " + observed);
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(4).toNanos();
        while (System.nanoTime() < deadline) {
            String now = Files.readString(greeter);
            if (!now.equals(seen)) {
                seen = now;
                runtime.changed(List.of(greeter), List.of());
                observed = observe(runtime, project, test);
                System.out.println("probe-observed: " + observed);
            }
            String last = read(done);
            if (last != null && last.equals(version(seen)) && last.equals(observed)) {
                break;
            }
            Thread.sleep(10);
        }
        // the edits are done: whatever the other process writes now, this one has nothing left to reload or run
        int before = test ? runtime.testRuns() : runtime.generation();
        Thread.sleep(4000);
        int after = test ? runtime.testRuns() : runtime.generation();
        if (after != before) {
            System.out.println("probe-error: " + (after - before) + " reload(s) or run(s) while idle");
        }
        observed = observe(runtime, project, test);
        System.out.println("probe-done: " + observed);
        System.out.flush();
        runtime.close();
        System.exit(0);
    }

    /**
     * What the current generation, or the last test run, sees of the edits: the version every helper class returns.
     */
    private static String observe(DevRuntime runtime, Path project, boolean test) {
        try {
            if (test) {
                Optional<TestRunSummary> run = runtime.lastTestRun();
                if (run.isEmpty() || !run.get().isSuccess()) {
                    System.out.println("probe-error: the tests did not pass: " + run.map(TestRunSummary::toString).orElse("no run"));
                    for (Path report : Files.list(project.resolve("build/test-results")).toList()) {
                        System.out.println(Files.readString(report));
                    }
                }
                return Files.readString(project.resolve("build/observed-test"));
            }
            if (runtime.lastFailure().isPresent()) {
                System.out.println("probe-error: " + runtime.lastFailure().get().describe());
            }
            Class<?> check = Class.forName("app.Check", true, runtime.classLoader().current());
            return (String) check.getMethod("all").invoke(null);
        } catch (Throwable e) {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            System.out.println("probe-error: " + trace);
            return "error";
        }
    }

    private static String version(String source) {
        int start = source.indexOf("return \"") + "return \"".length();
        return source.substring(start, source.indexOf('"', start));
    }

    private static String read(Path file) throws java.io.IOException {
        try {
            return Files.readString(file);
        } catch (NoSuchFileException e) {
            return null;
        }
    }
}
