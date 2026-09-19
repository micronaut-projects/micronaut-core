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
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The generated launcher and package initialisers bootstrap within a small thread stack.
 * <p>
 * GraalPy runs interpreted on a stock JDK and keeps dozens of Java frames per Python frame, and it
 * reports the Java {@code StackOverflowError} as a Python {@code RecursionError}. The shim packages of
 * this test suite import their subpackages eagerly, so the deepest import chain of the launcher nests
 * twelve package levels ({@code micronaut.test.extensions.junit5.annotation} through
 * {@code org.junit.jupiter.api} to {@code org.junit.platform.commons.annotation}), and every Python
 * frame a package initialiser adds per level is multiplied by twelve. Initialisers importing their
 * members modules through {@code importlib.import_module} from nested functions needed a 640 KB stack
 * on macOS arm64 (and failed intermittently at the 1 MB Linux x64 default), where initialisers that
 * run the members modules at their own level need 448 KB, as the initialisers before them did.
 */
class PythonBootstrapStackTest {

    /**
     * A stack on which the initialisers before the members modules bootstrapped and their first,
     * importing version did not, on every JVM tried; comfortably below the platform defaults.
     */
    private static final long STACK_SIZE = 576 * 1024;

    @Test
    void theLauncherBootstrapsWithinASmallThreadStack() throws Throwable {
        ClassLoader classLoader = getClass().getClassLoader();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread bootstrap = new Thread(null, () -> {
            Engine engine = GraalPyEngineFactory.buildPythonEngine();
            try {
                Context context = GraalPyContextFactory.buildContext(
                    GraalPyContextFactory.bootstrapHostAccess(classLoader), engine, classLoader);
                GraalPyContextFactory.closeContext(context);
            } catch (Throwable e) {
                failure.set(e);
            } finally {
                engine.close(true);
            }
        }, "python-bootstrap-small-stack", STACK_SIZE);
        bootstrap.start();
        bootstrap.join();
        if (failure.get() != null) {
            throw new AssertionError("the generated launcher did not bootstrap within a " + STACK_SIZE / 1024 + " KB thread stack", failure.get());
        }
    }
}
