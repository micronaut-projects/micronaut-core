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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;

import java.nio.file.Path;

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
        if (ContextHolder.isInitialized() && ContextHolder.isReuseContext()) {
            // Reuse context: this is an optimization for reloading
            return ContextHolder.getContext();
        }

        try {
            ClassLoader classLoader = applicationContext.getClassLoader();

            var builder = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                    .resourceDirectory(APPLICATION_PATH)
                    .resourceLoadingClass(classLoader.loadClass(PYRONAUT_MAIN_CLASS)).build())
                // restrict in future?
//                .option("python.ExposeInternalSources", StringUtils.TRUE)
//                .allowExperimentalOptions(true)
                // required for reloading
                .allowExperimentalOptions(true)
                .allowCreateProcess(true)
                .option("python.IsolateNativeModules", "true")
                .option("python.WarnExperimentalFeatures", "false")
                // Allow access to host classes
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(name -> true);
            var pyEnv = System.getenv("PYENV_VERSION");
            var venv = System.getenv("VIRTUAL_ENV");
            if (pyEnv != null && venv != null && pyEnv.startsWith("graalpy")) {
                builder.option("python.Executable", Path.of(venv).resolve("bin/python").toString());
            }

            var context = builder.build();
            context.initialize(GraalPyRuntimeUtil.PYTHON);

            // Make context available to bridge classes
            ContextHolder.setContext(context);

            return context;

        } catch (Exception e) {
            throw new ApplicationStartupException(
                "Failed to initialize GraalPy context: " + e.getMessage(), e);
        }
    }

    /**
     * Cleanup method called during application shutdown.
     * Resets the context in ContextHolder to prevent memory leaks.
     */
    @PreDestroy
    public void destroy() {
        if (!ContextHolder.isReuseContext()) {
            var ctx = ContextHolder.isInitialized() ? ContextHolder.getContext() : null;
            if (ctx != null) {
                ctx.close(true);
            }
            ContextHolder.resetContext();
        }
    }

}
