/*
 * Copyright 2017-2025 original authors
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

/**
 * Static holder for the GraalPy context used by Python bridge classes.
 * Provides thread-safe access to the shared Python execution context.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class ContextHolder {

    private static volatile Context context;

    private ContextHolder() {
        // Utility class
    }

    /**
     * Get the shared GraalPy context for Python execution.
     * The context is initialized lazily by the GraalPyContextFactory bean.
     *
     * @return The GraalPy context
     * @throws IllegalStateException if the context has not been initialized
     */
    public static Context getContext() {
        Context ctx = context;
        if (ctx == null) {
            throw new IllegalStateException("GraalPy context has not been initialized. " +
                "Make sure micronaut-context-python is on the classpath.");
        }
        return ctx;
    }

    /**
     * Set the GraalPy context. This method is called by GraalPyContextFactory
     * during application startup.
     *
     * @param context The GraalPy context to set
     */
    public static void setContext(Context context) {
        ContextHolder.context = context;
    }

    /**
     * Check if the context has been initialized.
     *
     * @return true if the context is available, false otherwise
     */
    public static boolean isInitialized() {
        return context != null;
    }

    /**
     * Reset the context to null. This method is called during application shutdown
     * to ensure proper cleanup and prevent memory leaks.
     */
    public static void resetContext() {
        context = null;
    }
}
