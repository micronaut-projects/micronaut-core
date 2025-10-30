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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

/**
 * Factory bean that creates and initializes the GraalPy context.
 * Loads the generated Python application script and makes the context
 * available via ContextHolder for bridge classes to use.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
@Factory
public class GraalPyContextFactory {
    private final ApplicationContext applicationContext;

    public GraalPyContextFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Create and initialize the GraalPy context.
     * This bean loads on startup due to the @Context annotation.
     *
     * @return The initialized GraalPy context
     */
    @io.micronaut.context.annotation.Context
    @Singleton
    public org.graalvm.polyglot.Context graalPyContext() {
        try {
            // Create GraalPy context
            org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder("python")
                .engine(Engine.create())
                .allowAllAccess(true)
                .build();

            // Load the generated Python application script
            loadPythonApplicationScript(context);

            // Make context available to bridge classes
            ContextHolder.setContext(context);

            return context;

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize GraalPy context", e);
        }
    }

    /**
     * Cleanup method called during application shutdown.
     * Resets the context in ContextHolder to prevent memory leaks.
     */
    @PreDestroy
    public void destroy() {
        ContextHolder.resetContext();
    }

    private void loadPythonApplicationScript(org.graalvm.polyglot.Context context) throws IOException {
        ClassLoader classLoader = applicationContext.getClassLoader();
        // Try to load the generated pyronaut_application.py from META-INF
        try (InputStream inputStream = classLoader
                .getResourceAsStream("META-INF/pyronaut_application.py")) {

            if (inputStream != null) {
                String scriptContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Source source = Source.newBuilder("python", scriptContent, "pyronaut_application.py")
                    .build();
                context.eval(source);
            }
        }
    }
}
