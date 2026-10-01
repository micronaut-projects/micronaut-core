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
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures the bootstrap of the Python context of this test suite: the time to build a context, the
 * modules the bootstrap imports from the generated sources, and the deepest Python frame chain an
 * import is reached from. Enabled by the {@code MICRONAUT_PYTHON_BENCHMARK} environment variable; the
 * results are printed and, when {@code MICRONAUT_PYTHON_BENCHMARK_OUTPUT} names a file, written to it
 * as JSON, so runs on different revisions can be compared.
 */
@EnabledIfEnvironmentVariable(named = "MICRONAUT_PYTHON_BENCHMARK", matches = "true")
class PythonBootstrapBenchmarkTest {

    private static final int CONTEXTS = Integer.parseInt(System.getenv().getOrDefault("MICRONAUT_PYTHON_BENCHMARK_CONTEXTS", "6"));

    private static final String COUNT_MODULES = """
        import sys
        generated = sorted(name for name, module in sys.modules.items()
                           if (getattr(module, '__file__', None) or '').startswith('/graalpy_vfs/src/'))
        fileless = sorted(name for name, module in sys.modules.items()
                          if getattr(module, '__spec__', None) is not None
                          and (getattr(module.__spec__, 'origin', None) or '').startswith('java:'))
        [len(sys.modules), len(generated), len(fileless), len([m for m in generated if not m.startswith(('micronaut.docs', 'example', 'routes', 'package_root', '__main__', 'micronaut_'))])]
        """;

    private static final String DEEPEST_IMPORT = """
        def __micronaut_rerun_launcher():
            import importlib.util
            import sys

            deepest = [0, None]

            class DepthRecorder:
                def find_spec(self, name, path=None, target=None):
                    depth = 0
                    frame = sys._getframe(1)
                    while frame is not None:
                        depth += 1
                        frame = frame.f_back
                    if depth > deepest[0]:
                        deepest[0] = depth
                        deepest[1] = name
                    return None

            for name, module in list(sys.modules.items()):
                file = getattr(module, '__file__', None) or ''
                origin = getattr(getattr(module, '__spec__', None), 'origin', None) or ''
                if file.startswith('/graalpy_vfs/src/') or origin.startswith('java:'):
                    del sys.modules[name]
            recorder = DepthRecorder()
            sys.meta_path.insert(0, recorder)
            try:
                spec = importlib.util.spec_from_file_location('__micronaut_launcher_rerun', '/graalpy_vfs/src/__main__.py')
                spec.loader.exec_module(importlib.util.module_from_spec(spec))
            finally:
                sys.meta_path.remove(recorder)
            return deepest

        __micronaut_rerun_launcher()
        """;

    private static final String MANIFEST_RELOAD = """
        import sys, time
        runtime = sys.modules.get('micronaut_java_imports') or sys.modules.get('micronaut_runtime')
        millis = None
        if runtime is not None and hasattr(runtime, '__micronaut_reset_java_imports'):
            runtime.__micronaut_reset_java_imports()
            started = time.perf_counter()
            runtime.__micronaut_java_imports()
            millis = round((time.perf_counter() - started) * 1000, 1)
        millis
        """;

    /**
     * The bootstrap phases log their durations at debug level; logback is on the runtime class path only.
     */
    private static void enableDebugLogging() {
        try {
            Object logger = org.slf4j.LoggerFactory.getLogger(GraalPyContextFactory.class);
            Class<?> levelClass = Class.forName("ch.qos.logback.classic.Level");
            Object debug = levelClass.getField("DEBUG").get(null);
            logger.getClass().getMethod("setLevel", levelClass).invoke(logger, debug);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("PYTHON_BOOTSTRAP_BENCHMARK debug logging unavailable: " + e);
        }
    }

    @Test
    void measureBootstrap() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        enableDebugLogging();
        List<String> rows = new ArrayList<>();
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine()) {
            // the contexts of an engine share one host access configuration
            org.graalvm.polyglot.HostAccess hostAccess = GraalPyContextFactory.bootstrapHostAccess(classLoader);
            for (int run = 1; run <= CONTEXTS; run++) {
                long start = System.nanoTime();
                Context context = GraalPyContextFactory.buildContext(hostAccess, engine, classLoader);
                // the runtime module is imported on the first bridge call at the latest; the bootstrap of this
                // revision may import it earlier, so both count it
                PythonContextRuntime.helper(context, "__micronaut_inspect_isclass");
                long buildMillis = (System.nanoTime() - start) / 1_000_000;
                try {
                    Value counts = context.eval(PythonContextRuntime.PYTHON, COUNT_MODULES);
                    long modules = counts.getArrayElement(0).asLong();
                    long generated = counts.getArrayElement(1).asLong();
                    long fileless = counts.getArrayElement(2).asLong();
                    long shims = counts.getArrayElement(3).asLong();
                    Value deepest = context.eval(PythonContextRuntime.PYTHON, DEEPEST_IMPORT);
                    int depth = deepest.getArrayElement(0).asInt();
                    String module = deepest.getArrayElement(1).asString();
                    // the time the runtime takes to read the Java import manifests again (this revision only)
                    Value manifestMillis = context.eval(PythonContextRuntime.PYTHON, MANIFEST_RELOAD);
                    String row = String.format(
                        "{\"run\": %d, \"buildMillis\": %d, \"modules\": %d, \"generatedModules\": %d, \"javaShimModules\": %d, \"javaPackageModules\": %d, \"deepestImportFrames\": %d, \"deepestImport\": \"%s\", \"manifestReloadMillis\": %s}",
                        run, buildMillis, modules, generated, shims, fileless, depth, module, manifestMillis.isNumber() ? String.valueOf(manifestMillis.asDouble()) : "null");
                    System.out.println("PYTHON_BOOTSTRAP_BENCHMARK " + row);
                    rows.add(row);
                } finally {
                    GraalPyContextFactory.closeContext(context);
                }
            }
        }
        assertEquals(CONTEXTS, rows.size(), "every context was measured");
        String output = System.getenv("MICRONAUT_PYTHON_BENCHMARK_OUTPUT");
        if (output != null && !output.isBlank()) {
            Files.writeString(Path.of(output), "[\n" + String.join(",\n", rows) + "\n]\n", StandardCharsets.UTF_8);
        }
    }
}
