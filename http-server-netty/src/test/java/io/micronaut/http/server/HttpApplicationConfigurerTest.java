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
package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The {@link ApplicationContextConfigurer}s of the application run for an {@link HttpApplication}, as for
 * {@code Micronaut.run}: the builder hook when the application is built, before its own settings, and the context
 * hook when the context starts.
 */
class HttpApplicationConfigurerTest {

    private static final String SPEC_NAME = "HttpApplicationConfigurerTest";

    @Test
    void configurersRun(@TempDir Path services) throws Exception {
        // the configurer is a service only for this test: a loader of its own lists it
        Path file = services.resolve("META-INF/services/" + ApplicationContextConfigurer.class.getName());
        Files.createDirectories(file.getParent());
        Files.writeString(file, RecordingConfigurer.class.getName());

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {services.toUri().toURL()}, previous)) {
            thread.setContextClassLoader(loader);
            try (ApplicationContext context = HttpApplication.build()
                .port(-1)
                .property("spec.name", SPEC_NAME)
                .property(RecordingConfigurer.OVERRIDDEN, "application")
                .routes(routes -> routes.GET("/", (request, vars) -> HttpResponse.ok()))
                .start()) {

                assertEquals("configurer", context.getProperty(RecordingConfigurer.BUILDER, String.class).orElse(null));
                assertEquals("application", context.getProperty(RecordingConfigurer.OVERRIDDEN, String.class).orElse(null),
                    "the settings of the HttpApplication are applied after the configurer");
                assertSame(context, RecordingConfigurer.configured, "the context hook ran for the started context");
            }
        } finally {
            thread.setContextClassLoader(previous);
            RecordingConfigurer.configured = null;
        }
    }

    /**
     * Records both hooks.
     */
    public static final class RecordingConfigurer implements ApplicationContextConfigurer {

        static final String BUILDER = "http-application-configurer-test.builder";
        static final String OVERRIDDEN = "http-application-configurer-test.overridden";

        static volatile ApplicationContext configured;

        @Override
        public void configure(ApplicationContextBuilder builder) {
            builder.properties(Map.of(BUILDER, "configurer", OVERRIDDEN, "configurer"));
        }

        @Override
        public void configure(ApplicationContext applicationContext) {
            configured = applicationContext;
        }
    }
}
