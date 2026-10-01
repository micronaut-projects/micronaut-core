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
package io.micronaut.dev.test;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.reflect.ClassUtils;
import org.jspecify.annotations.NullMarked;

/**
 * Runs tests on the JUnit Platform, with every engine on the launch classpath: Jupiter, Spock, Kotest, or
 * Pyronaut's pytest engine. Available when {@code junit-platform-launcher} is on the launch classpath; the
 * module does not depend on it.
 *
 * <p>The test classes are given to the engines as classes loaded through the request's loader, or found
 * by scanning the test outputs through it when every test runs, so engines in the parent tier see the
 * classes of the generation. Standard output and error are captured per test. Patterns filter by class
 * and method, in the form described by {@link TestSelection}. The configuration parameters of the request
 * are passed to the engines.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class JUnitPlatformTestRunner implements TestRunner {

    /**
     * The runner's identifier.
     */
    public static final String ID = "junit-platform";

    private static final String LAUNCHER = "org.junit.platform.launcher.core.LauncherFactory";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return ClassUtils.isPresent(LAUNCHER, JUnitPlatformTestRunner.class.getClassLoader());
    }

    @Override
    public TestRunSummary run(TestRunRequest request, TestEventListener listener, Cancellation cancellation) {
        if (!isAvailable()) {
            throw new IllegalStateException("junit-platform-launcher is not on the classpath: development mode runs tests on the JUnit Platform of the project's test runtime");
        }
        // the JUnit types are referenced by that class alone, so this one loads without the platform present
        return new JUnitPlatformExecution(request, listener, cancellation).run();
    }
}
