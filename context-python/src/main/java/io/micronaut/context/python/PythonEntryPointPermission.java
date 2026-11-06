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

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.core.annotation.Internal;

import java.lang.ref.WeakReference;

/**
 * Whether generated Python code that finds no runtime and no recorded application context is a
 * platform entry point, which gets a default context, rather than a leftover reference of an
 * application that shut down, which must fail.
 * <p>
 * It is one, in only two cases. No application has been recorded in this JVM yet, so the code cannot
 * be a leftover of one. Or an application is on its way on the calling thread: the platform runs the
 * entry points of an application ({@code TestPropertyProvider.getProperties()}, the constructor of a
 * {@code contextBuilder}) on the thread that fills in its builder, before the application is
 * recorded. The builder stops being on its way when it produces its application or when it is
 * collected, so one that is abandoned leaves nothing behind.
 * <p>
 * Both are decided here and nowhere else, because every application context builder in the JVM goes
 * through {@link #expect(ApplicationContextBuilder)}, those of applications that never touch Python
 * included: this class has no polyglot reference and no logger, so that path loads nothing else.
 * <p>
 * The class is initialized at run time in a native image (it is on no
 * {@code --initialize-at-build-time} list, and the configurer calling it is initialized at build time
 * without running any of its methods), so an image starts with no application recorded whatever
 * happened while it was built.
 *
 * @author Micronaut Team
 * @since 5.2.23
 */
@Internal
final class PythonEntryPointPermission {

    // the newest builder of each thread. The value is a plain WeakReference, so a pooled thread that
    // outlives the class loader of the application holds neither the builder nor a class of that loader
    private static final ThreadLocal<WeakReference<ApplicationContextBuilder>> ON_ITS_WAY = new ThreadLocal<>();
    private static volatile boolean applicationRecorded;

    private PythonEntryPointPermission() {
    }

    /**
     * Record that the application of a builder is on its way on the calling thread.
     *
     * @param builder The builder
     * @return The reference that keeps it on its way: the builder is no longer on its way once the
     * reference is cleared, by {@link WeakReference#clear()} from any thread or by the collector
     */
    static WeakReference<ApplicationContextBuilder> expect(ApplicationContextBuilder builder) {
        WeakReference<ApplicationContextBuilder> onItsWay = new WeakReference<>(builder);
        ON_ITS_WAY.set(onItsWay);
        return onItsWay;
    }

    /**
     * Record that an application has been recorded in this JVM, so generated code reached with no
     * application on its way can only be a leftover reference from here on.
     */
    static void applicationRecorded() {
        applicationRecorded = true;
    }

    /**
     * Forget the builder that is on its way on the calling thread.
     */
    static void revoke() {
        ON_ITS_WAY.remove();
    }

    /**
     * Whether generated code on the calling thread that finds no runtime and no recorded application
     * context may build a default context for itself.
     *
     * @return {@code true} when it is a platform entry point rather than a leftover reference
     */
    static boolean isGranted() {
        if (!applicationRecorded) {
            return true;
        }
        WeakReference<ApplicationContextBuilder> onItsWay = ON_ITS_WAY.get();
        return onItsWay != null && onItsWay.get() != null;
    }

    /**
     * Back to the state of a JVM in which no application has been recorded, for the test of that state.
     */
    static void reset() {
        applicationRecorded = false;
        revoke();
    }
}
