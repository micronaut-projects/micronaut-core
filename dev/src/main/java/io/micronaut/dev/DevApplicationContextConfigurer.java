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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.BeanContextConfiguration;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.manifest.DevMode;
import io.micronaut.scheduling.io.watch.DirectoryWatcher;
import io.micronaut.scheduling.io.watch.FileWatchConfiguration;
import io.micronaut.scheduling.io.watch.FileWatcher;

import java.util.Map;

/**
 * Configures every application context an application builds while a {@link DevRuntime} runs the
 * process: the reloadable class loader, the {@code dev} environment, the HTTP server on the loopback address unless
 * configured otherwise, the retained beans of the
 * previous generation, the runtime itself as a bean, and a {@link FileWatcher} backed by the runtime's one
 * watcher. Service-loaded through the thread context loader, which the launcher points at the
 * reloadable tier; does nothing outside development mode.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DevApplicationContextConfigurer implements ApplicationContextConfigurer {

    static final String LOOPBACK_SOURCE = "micronaut-dev-loopback";
    private static final String SERVER_HOST = "micronaut.server.host";
    private static final String LOCALHOST = "localhost";

    @Override
    public void configure(ApplicationContextBuilder builder) {
        DevRuntime runtime = DevRuntime.current();
        if (runtime == null) {
            return;
        }
        if (runtime.manifest().mode() == DevMode.TEST) {
            // in test mode the tests start their own contexts, as they would in the build, on the generation that
            // loaded them: a context built without a class of the tests, ApplicationContext.run(), would otherwise
            // load the beans of the launcher's loader, which holds none of the project's; a loader the test chose stays
            if (builder instanceof BeanContextConfiguration configuration && configuration.getClassLoader() == runtime.classLoader().getParent()) {
                builder.classLoader(runtime.classLoader().current());
            }
            // not development mode, which would change how the tests run, but what modules may keep warm across runs
            builder.properties(Map.of(DevelopmentMode.TEST_PROPERTY, true));
            return;
        }
        // the generation loader itself, never the facade: the JVM records the loader Class.forName was called
        // with as the initiating loader of a class, and would hand the next generation the previous classes
        builder.classLoader(runtime.classLoader().current());
        builder.environments(Environment.DEVELOPMENT);
        builder.properties(Map.of(
            DevelopmentMode.PROPERTY, true,
            // the engine watches; the context's own watcher must neither restart the JVM nor run beside it
            FileWatchConfiguration.RESTART, false,
            FileWatchConfiguration.ENABLED, false
        ));
        // run mode only: test mode returned above and leaves the host as the tests configure it
        if (runtime.manifest().bindLoopback()) {
            builder.propertySources(loopback());
        }
        builder.beanDependencyTrackingEnabled(true);
        builder.retainedRegistrations(runtime.takeRetainedRegistrations());
        builder.singletons(runtime);
    }

    @Override
    public void configure(ApplicationContext applicationContext) {
        DevRuntime runtime = DevRuntime.current();
        if (runtime == null) {
            return;
        }
        runtime.contextCreated(applicationContext);
        DirectoryWatcher watcher = runtime.fileWatcher();
        if (watcher != null && runtime.manifest().mode() != DevMode.TEST) {
            // the context's own watch thread is off: modules register with the engine's one watcher instead, through a
            // view that closes their registrations when this context stops
            applicationContext.registerSingleton(FileWatcher.class, new DevFileWatcher(watcher, runtime.pinnedWatches(), applicationContext.getClassLoader()));
        }
    }

    /**
     * The server host of a development launch, {@code localhost}, at the lowest precedence: the application's
     * configuration, an environment variable and a system property all win, so a developer who binds elsewhere does.
     *
     * @return The property source
     */
    static PropertySource loopback() {
        return PropertySource.of(LOOPBACK_SOURCE, Map.<String, Object>of(SERVER_HOST, LOCALHOST), Integer.MIN_VALUE);
    }

    @Override
    public int getOrder() {
        // after the application's own configurer, so that the reloadable loader and the environment win
        return LOWEST_PRECEDENCE;
    }
}
