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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated launcher and package initialisers keep the import chain of the bootstrap shallow.
 * <p>
 * GraalPy runs interpreted on a stock JDK and keeps dozens of Java frames per Python frame, and it
 * reports the Java {@code StackOverflowError} as a Python {@code RecursionError}. While the shim
 * packages of this test suite imported their subpackages eagerly, the deepest import chain of the
 * launcher nested twelve package levels ({@code micronaut.test.extensions.junit5.annotation} through
 * {@code org.junit.jupiter.api} to {@code org.junit.platform.commons.annotation}), and every Python
 * frame a package initialiser adds per level was multiplied by twelve. Initialisers importing their
 * members modules through {@code importlib.import_module} from nested functions needed a 640 KB stack
 * on macOS arm64 and failed intermittently at the 1 MB default of Linux x64; initialisers running the
 * members modules at their own level reached an import from 167 Python frames, and packages importing
 * their subpackages on first access from 55. The Java packages are now served by the import finder of
 * {@code micronaut_java_imports} without generated modules, so importing one nests no package
 * initialiser at all, and the deepest import of the launcher is reached from 23 frames.
 * <p>
 * The stack a frame takes depends on the platform, the number of Python frames does not: the test
 * re-runs the launcher with an import hook that records the deepest Python frame chain an import is
 * reached from, and bounds it.
 */
class PythonBootstrapStackTest {

    /**
     * Above the deepest import chain now that the Java packages are served by the import finder without
     * generated modules (23 frames, reaching {@code micronaut.test}), below the one of the generated
     * initialisers that imported their subpackages on first access (55 frames).
     */
    private static final int MAX_IMPORT_DEPTH = Integer.getInteger("micronaut.test.python.max-import-depth", 40);

    private static final String RERUN_LAUNCHER_WITH_DEPTH_RECORDER = """
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

            # the generated modules import again, from the launcher down
            for name, module in list(sys.modules.items()):
                if (getattr(module, '__file__', None) or '').startswith('/graalpy_vfs/src/'):
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

    @Test
    void theLauncherImportChainStaysShallow() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine()) {
            Context context = GraalPyContextFactory.buildContext(
                GraalPyContextFactory.bootstrapHostAccess(classLoader), engine, classLoader);
            try {
                Value deepest = context.eval(PythonContextRuntime.PYTHON, RERUN_LAUNCHER_WITH_DEPTH_RECORDER);
                int depth = deepest.getArrayElement(0).asInt();
                String module = deepest.getArrayElement(1).asString();
                assertTrue(depth <= MAX_IMPORT_DEPTH, "the launcher imports [" + module + "] from a chain of "
                    + depth + " Python frames, more than " + MAX_IMPORT_DEPTH);
            } finally {
                GraalPyContextFactory.closeContext(context);
            }
        }
    }
}
