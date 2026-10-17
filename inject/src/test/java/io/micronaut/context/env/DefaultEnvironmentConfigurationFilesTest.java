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
package io.micronaut.context.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEnvironmentConfigurationFilesTest {

    @TempDir
    Path root;

    @Test
    void theApplicationFilesOfTheDefaultLocationsInEveryFormatAndActiveEnvironment() throws IOException {
        Files.writeString(root.resolve("application.yml"), "app.name: test\n");
        try (ApplicationContext context = builder().environments("dev").start()) {
            Environment environment = context.getEnvironment();
            assertTrue(environment.getConfigurationFiles().contains("classpath:application.yml"));
            assertTrue(environment.isConfigurationFile("classpath:application.yml"));
            assertTrue(environment.isConfigurationFile("classpath:/application.yaml"));
            // looked for whether or not they exist
            assertTrue(environment.isConfigurationFile("classpath:application-dev.properties"));
            assertTrue(environment.isConfigurationFile("file:config/application-dev.yml"));
            assertTrue(environment.isConfigurationFile(Path.of("config", "application.properties").toAbsolutePath().toString()));
            assertFalse(environment.isConfigurationFile("classpath:applications.yml"));
            assertFalse(environment.isConfigurationFile("classpath:application-other.yml"));
            assertFalse(environment.isConfigurationFile("classpath:config/application.yml"));
            assertFalse(environment.isConfigurationFile("classpath:schema.graphqls"));
            assertFalse(environment.isConfigurationFile("classpath:messages.properties"));
        }
    }

    @Test
    void theApplicationFilesOfOverriddenConfigurationLocations() throws IOException {
        Path external = root.resolve("external");
        Files.createDirectories(root.resolve("config"));
        Files.writeString(root.resolve("config/application.yml"), "app.name: config\n");
        try (ApplicationContext context = builder().environments("dev").overrideConfigLocations("classpath:config/", "file:" + external).start()) {
            Environment environment = context.getEnvironment();
            assertEquals("config", environment.getProperty("app.name", String.class).orElse(null));
            assertTrue(environment.isConfigurationFile("classpath:config/application.yml"));
            assertTrue(environment.isConfigurationFile("classpath:config/application-dev.properties"));
            // a file location that does not exist yet
            assertTrue(environment.isConfigurationFile("file:" + external.resolve("application-dev.yml")));
            assertFalse(environment.isConfigurationFile("classpath:application.yml"));
            assertFalse(environment.isConfigurationFile("file:config/application.yml"));
            assertFalse(environment.isConfigurationFile("classpath:config/schema.graphqls"));
        }
    }

    @Test
    void theFilesImportsDeclareLoadedOrNot() throws IOException {
        Path absolute = root.resolve("abs.yml").toAbsolutePath();
        Files.writeString(root.resolve("application.yml"), """
            micronaut:
              config:
                import:
                  - optional:classpath://feature
                  - optional:classpath://late.yml
                  - optional:file://%s
                  - classpath://nested/app.yml
            """.formatted(absolute.toString().replace('\\', '/')));
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/app.yml"), "app.nested: true\n");
        Files.writeString(root.resolve("application-scalar.properties"), "micronaut.config.import=optional:classpath://scalar.yml\n");
        Files.writeString(root.resolve("application-indexed.properties"), "micronaut.config.import[0]=optional:classpath://indexed/one.yml\n");
        Files.writeString(root.resolve("application-structured.properties"), """
            micronaut.config.import[0].provider=classpath
            micronaut.config.import[0].resource-path=structured/two.yml
            micronaut.config.import[0].optional=true
            """);
        try (ApplicationContext context = builder().environments("scalar", "indexed", "structured").start()) {
            Environment environment = context.getEnvironment();
            assertTrue(environment.getProperty("app.nested", Boolean.class).orElse(false));
            // a list, a scalar, an indexed and a structured import, none of whose files exists
            assertTrue(environment.isConfigurationFile("classpath:late.yml"));
            assertTrue(environment.isConfigurationFile("classpath:scalar.yml"));
            assertTrue(environment.isConfigurationFile("classpath:indexed/one.yml"));
            assertTrue(environment.isConfigurationFile("classpath:structured/two.yml"));
            assertTrue(environment.isConfigurationFile("classpath:nested/app.yml"));
            // an absolute file, given with the separators of the platform or with forward slashes
            assertTrue(environment.isConfigurationFile(absolute.toString()));
            assertTrue(environment.isConfigurationFile("file:" + absolute.toString().replace('\\', '/')));
            // an import without an extension, of a file in any format a loader reads
            assertTrue(environment.isConfigurationFile("classpath:feature.yml"));
            assertTrue(environment.isConfigurationFile("classpath:feature.properties"));
            assertFalse(environment.isConfigurationFile("classpath:feature.graphqls"));
            assertFalse(environment.isConfigurationFile("classpath:notlate.yml"));
            assertFalse(environment.isConfigurationFile("classpath:nested/late.yml"));

            // an optional import created later is read by a refresh
            Files.writeString(root.resolve("late.yml"), "app.late: true\n");
            environment.refresh();
            assertTrue(environment.getProperty("app.late", Boolean.class).orElse(false));
            assertTrue(environment.isConfigurationFile("classpath:late.yml"));
        }
    }

    @Test
    void theBootstrapFilesOfTheBootstrapEnvironment() throws IOException {
        Files.writeString(root.resolve("bootstrap.yml"), "app.bootstrap: true\n");
        try (ApplicationContext context = builder().bootstrapEnvironment(true).environments("dev").start()) {
            Environment environment = context.getEnvironment();
            assertTrue(environment.getProperty("app.bootstrap", Boolean.class).orElse(false));
            assertTrue(environment.isConfigurationFile("classpath:bootstrap.yml"));
            assertTrue(environment.isConfigurationFile("classpath:bootstrap-dev.properties"));
            assertTrue(environment.isConfigurationFile("classpath:application.yml"));
        }
    }

    @Test
    void theFilesOfTheSourcesALocatorReads() throws IOException {
        Path custom = root.resolve("custom.properties").toAbsolutePath();
        PropertySourcesLocator locator = environment -> List.of(
            PropertySource.of("custom", Map.of("app.custom", true), PropertySource.Origin.of("file:" + custom)));
        try (ApplicationContext context = builder().propertySourcesLocator(locator).start()) {
            Environment environment = context.getEnvironment();
            assertTrue(environment.getProperty("app.custom", Boolean.class).orElse(false));
            assertTrue(environment.isConfigurationFile(custom.toString()));
        }
    }

    @Test
    void locationsAreComparedInOneForm() {
        assertEquals("classpath:config/app.yml", DefaultEnvironment.normalizeConfigurationFile("classpath:/config/app.yml"));
        assertEquals("classpath:config/app.yml", DefaultEnvironment.normalizeConfigurationFile("classpath*:config/./app.yml"));
        assertEquals("classpath:config/app.yml", DefaultEnvironment.normalizeConfigurationFile("classpath:config\\app.yml"));
        String file = "file:" + Path.of("config", "app.yml").toAbsolutePath().toString().replace('\\', '/');
        assertEquals(file, DefaultEnvironment.normalizeConfigurationFile("config/app.yml"));
        assertEquals(file, DefaultEnvironment.normalizeConfigurationFile("file:config/../config/app.yml"));
    }

    @Test
    void anotherEnvironmentNormalizesTheLocationItIsAsked() {
        String file = "file:" + Path.of("config", "app.yml").toAbsolutePath().toString().replace('\\', '/');
        Environment environment = (Environment) Proxy.newProxyInstance(Environment.class.getClassLoader(), new Class<?>[]{Environment.class}, (proxy, method, args) -> {
            if (method.getName().equals("getConfigurationFiles")) {
                return Set.of("classpath:config/application.yml", file);
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            throw new UnsupportedOperationException(method.getName());
        });
        assertTrue(environment.isConfigurationFile("classpath:config/application.yml"));
        assertTrue(environment.isConfigurationFile("classpath:/config/application.yml"));
        assertTrue(environment.isConfigurationFile("classpath*:config/./application.yml"));
        assertTrue(environment.isConfigurationFile("classpath:config\\application.yml"));
        assertTrue(environment.isConfigurationFile("config/app.yml"));
        assertTrue(environment.isConfigurationFile("file:config/../config/app.yml"));
        assertTrue(environment.isConfigurationFile(Path.of("config", "app.yml").toAbsolutePath().toString()));
        assertFalse(environment.isConfigurationFile("classpath:application.yml"));
        assertFalse(environment.isConfigurationFile("classpath:app.yml"));
        assertFalse(environment.isConfigurationFile("config/other.yml"));
    }

    private ApplicationContextBuilder builder() throws MalformedURLException {
        return ApplicationContext.builder()
            .classLoader(classLoader(root))
            .deduceEnvironment(false)
            .deduceCloudEnvironment(false)
            .environmentPropertySource(false)
            .banner(false);
    }

    /**
     * A class loader whose resources are those of the root, and of the test class path only for service
     * descriptors: the configuration files of the test class path stay out.
     */
    private static ClassLoader classLoader(Path root) throws MalformedURLException {
        ClassLoader services = new ClassLoader(DefaultEnvironmentConfigurationFilesTest.class.getClassLoader()) {
            @Override
            public URL getResource(String name) {
                return name.startsWith("META-INF/") ? super.getResource(name) : null;
            }

            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return name.startsWith("META-INF/") ? super.getResources(name) : Collections.emptyEnumeration();
            }
        };
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, services);
    }
}
