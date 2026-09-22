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
package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Classes of one Python package resolved on several threads while the package is not imported yet.
 */
final class PythonPackageImportConcurrencyTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesClassesOfAPackageNotImportedYetOnConcurrentThreads() throws Exception {
        writePackage("concurrent", "");
        try (Context context = newContext()) {
            // the first import of the package is held until another thread waits for the lock of the package,
            // so the threads reach the import system together whichever way the threads are scheduled
            context.eval(PYTHON, """
                import importlib._bootstrap as bootstrap
                import sys
                import time

                class HoldPackageImport:
                    def __init__(self, package):
                        self.package = package
                        self.held = False

                    def find_spec(self, name, path, target=None):
                        if name == self.package and not self.held:
                            self.held = True
                            lock = bootstrap._get_module_lock(self.package)
                            deadline = time.monotonic() + 30
                            while not lock.waiters and time.monotonic() < deadline:
                                time.sleep(0.01)
                        return None

                sys.meta_path.insert(0, HoldPackageImport("example.concurrent"))
                """);

            List<Value> classes = resolveConcurrently(context, List.of(
                classReference("example.concurrent", "First"),
                classReference("example.concurrent", "Second"),
                classReference("example.concurrent", "First")
            ));

            assertEquals("first", classes.get(0).getMember("value").asString());
            assertEquals("second", classes.get(1).getMember("value").asString());
            assertEquals("first", classes.get(2).getMember("value").asString());
        }
    }

    @Test
    void resolvesAClassOnAnotherThreadWhileItsPackageIsBeingImported() throws Exception {
        // a call made while a module of the package is executed resolves a second class of the package on
        // another thread and waits for it (a service the parallel service loader creates at import time)
        writePackage("callback", """
            import polyglot
            SECOND = polyglot.import_value("resolve_second")()
            """);
        try (Context context = newContext()) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                context.getPolyglotBindings().putMember("resolve_second", (ProxyExecutable) arguments -> {
                    Future<Value> second = executor.submit(() -> PythonContextRuntime.findClass(classReference("example.callback", "Second"), context));
                    try {
                        return second.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException("The class was not resolved on the other thread", e);
                    }
                });

                Value first = assertTimeoutPreemptively(TIMEOUT, () -> PythonContextRuntime.findClass(classReference("example.callback", "First"), context));

                assertEquals("first", first.getMember("value").asString());
                assertEquals("second", context.eval(PYTHON, "import sys\nsys.modules['example.callback.First'].SECOND.value").asString());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static List<Value> resolveConcurrently(Context context, List<PythonContextRuntime.PythonClassReference> references) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(references.size());
        try {
            CyclicBarrier start = new CyclicBarrier(references.size());
            List<Future<Value>> futures = new ArrayList<>();
            for (PythonContextRuntime.PythonClassReference reference : references) {
                Callable<Value> resolve = () -> {
                    start.await();
                    return PythonContextRuntime.findClass(reference, context);
                };
                futures.add(executor.submit(resolve));
            }
            return assertTimeoutPreemptively(TIMEOUT, () -> {
                List<Value> classes = new ArrayList<>();
                for (Future<Value> future : futures) {
                    classes.add(future.get());
                }
                return classes;
            });
        } finally {
            executor.shutdownNow();
        }
    }

    private Context newContext() {
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        context.getBindings(PYTHON).putMember("root", temporaryDirectory.toString());
        context.eval(PYTHON, "import sys\nsys.path.insert(0, root)");
        return context;
    }

    /**
     * A package {@code example.<name>} of two modules named after their classes, with the initialiser the
     * Python compiler generates: it imports every module of the package.
     */
    private void writePackage(String name, String firstModulePrefix) throws IOException {
        Path packagePath = temporaryDirectory.resolve("example").resolve(name);
        Files.createDirectories(packagePath);
        Files.writeString(temporaryDirectory.resolve("example").resolve("__init__.py"), "");
        Files.writeString(packagePath.resolve("__init__.py"), """
            from .First import First
            from .Second import Second

            __all__ = ["First", "Second"]
            """);
        Files.writeString(packagePath.resolve("First.py"), firstModulePrefix + """

            class First:
                value = "first"
            """);
        Files.writeString(packagePath.resolve("Second.py"), """
            class Second:
                value = "second"
            """);
    }

    private static PythonContextRuntime.PythonClassReference classReference(String packageName, String className) {
        return new PythonContextRuntime.PythonClassReference(
            packageName,
            className,
            new String[0],
            className,
            "class-instance:" + packageName + "." + className
        );
    }
}
