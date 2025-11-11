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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
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
    public static final String PYTHON = "python";
    public static final String APPLICATION_PATH = "META-INF/GRAALPY-VFS/micronaut-application";
    public static final String APPLICATION_LAUNCHER_PATH = APPLICATION_PATH + "/main.py";
    public static final String PYRONAUT_MAIN_CLASS = "pyronaut_application.PyronautMain";

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
            ClassLoader classLoader = applicationContext.getClassLoader();

            Context context = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                    .resourceDirectory(APPLICATION_PATH)
                    .resourceLoadingClass(classLoader.loadClass(PYRONAUT_MAIN_CLASS)).build())
                // restrict in future?
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(name -> true)
                .build();
            context.initialize(PYTHON);



            // Try to load the generated pyronaut_application.py from META-INF
            try (InputStream inputStream = classLoader
                .getResourceAsStream(APPLICATION_LAUNCHER_PATH)) {

                if (inputStream != null) {
                    String scriptContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    Source source = Source.newBuilder(PYTHON, scriptContent, "main.py")
                        .build();
                    context.eval(source);
                }
            }

            // Make context available to bridge classes
            ContextHolder.setContext(context);

            return context;

        } catch (Exception e) {
            throw new ApplicationStartupException("Failed to initialize GraalPy context: " + e.getMessage(), e);
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

}
