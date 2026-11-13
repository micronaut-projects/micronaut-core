package io.micronaut.context.env;

import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.reflect.ClassUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentLoggingTest {
    @Test
    void loggingRemainsEnabledByDefault() {
        ApplicationContextConfiguration configuration = List::of;
        assertTrue(configuration.isEnvironmentLoggingEnabled());
    }

    @Test
    void quietEnvironmentDoesNotLoadLoggerFactory(@TempDir Path root) throws Exception {
        Path tree = Files.createDirectory(root.resolve("tree"));
        Files.writeString(tree.resolve("probe.tree"), "loaded\n");
        Files.writeString(root.resolve("application.properties"), "probe.properties=loaded\nprobe.placeholder=${probe.properties}\n"
            + "micronaut.config.import=configtree://" + tree.toString().replace("\\", "\\\\") + "\n");
        Files.writeString(root.resolve("application.yml"), "probe:\n  yaml: loaded\n");
        Files.writeString(root.resolve("extra.properties"), "probe.extra=loaded\n");
        URL importerServices = Environment.class.getClassLoader().getResource(
            "META-INF/services/io.micronaut.context.env.PropertySourceImporter");
        assertNotNull(importerServices);
        URL[] urls = {
            root.toUri().toURL(),
            Probe.class.getProtectionDomain().getCodeSource().getLocation(),
            Environment.class.getProtectionDomain().getCodeSource().getLocation(),
            importerServices.toURI().resolve("../../").toURL(),
            ClassUtils.class.getProtectionDomain().getCodeSource().getLocation(),
            Logger.class.getProtectionDomain().getCodeSource().getLocation(),
            Yaml.class.getProtectionDomain().getCodeSource().getLocation(),
            jakarta.inject.Inject.class.getProtectionDomain().getCodeSource().getLocation()
        };
        String previous = System.getProperty(Environment.PROPERTY_SOURCES_KEY);
        String previousClassloaderLogging = System.getProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING);
        // Other tests start application contexts, which enable this separate global diagnostic option.
        System.setProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING, "false");
        System.setProperty(Environment.PROPERTY_SOURCES_KEY, "classpath:extra.properties");
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("org.slf4j.LoggerFactory")) {
                    throw new AssertionError("Quiet environment attempted to load LoggerFactory");
                }
                return super.loadClass(name, resolve);
            }
        }) {
            assertDoesNotThrow(() -> Class.forName(Probe.class.getName(), true, loader).getMethod("run").invoke(null));
        } finally {
            if (previousClassloaderLogging == null) {
                System.clearProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING);
            } else {
                System.setProperty(ClassUtils.PROPERTY_MICRONAUT_CLASSLOADER_LOGGING, previousClassloaderLogging);
            }
            if (previous == null) {
                System.clearProperty(Environment.PROPERTY_SOURCES_KEY);
            } else {
                System.setProperty(Environment.PROPERTY_SOURCES_KEY, previous);
            }
        }
    }

    @Test
    void configTreeImportsSkipHiddenFilesWithEitherLoggingSetting(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("probe.tree"), "loaded\n");
        Files.writeString(root.resolve(".hidden"), "hidden\n");
        Path secrets = Files.createDirectory(root.resolve(".secrets"));
        Files.writeString(secrets.resolve("password"), "secret\n");
        for (boolean loggingEnabled : new boolean[]{true, false}) {
            ApplicationContextConfiguration configuration = new ApplicationContextConfiguration() {
                @Override
                public List<String> getEnvironments() {
                    return List.of();
                }

                @Override
                public boolean isEnvironmentLoggingEnabled() {
                    return loggingEnabled;
                }

                @Override
                public List<String> getOverrideConfigLocations() {
                    return List.of();
                }
            };
            try (Environment environment = Environment.create(configuration)) {
                environment.addPropertySource(PropertySource.of("config-tree", Map.of(
                    "micronaut.config.import", "configtree://" + root
                )));
                environment.start();
                assertEquals("loaded", environment.getRequiredProperty("probe.tree", String.class));
                assertFalse(environment.containsProperty(".hidden"));
                assertFalse(environment.containsProperty(".secrets.password"));
                environment.refresh();
                assertEquals("loaded", environment.getRequiredProperty("probe.tree", String.class));
                assertFalse(environment.containsProperty(".hidden"));
                assertFalse(environment.containsProperty(".secrets.password"));
            }
        }
    }

    public static class Probe {
        public static void run() throws Exception {
            ApplicationContextConfiguration configuration = new ApplicationContextConfiguration() {
                @Override
                public List<String> getEnvironments() {
                    return List.of();
                }

                @Override
                public boolean isEnvironmentLoggingEnabled() {
                    return false;
                }

                @Override
                public ClassLoader getClassLoader() {
                    return Probe.class.getClassLoader();
                }

                @Override
                public List<String> getOverrideConfigLocations() {
                    return List.of("classpath:/");
                }
            };
            ClassPathResourceLoader resources = configuration.getResourceLoader();
            try (var input = resources.forBase("classpath:/").getResourceAsStream("application.properties")
                .orElseThrow(() -> new AssertionError("Derived loader failed to read properties"))) {
                if (input.read() == -1) {
                    throw new AssertionError("Derived loader returned empty properties");
                }
            }
            try (Environment environment = Environment.create(configuration)) {
                environment.start();
                verify(environment, "probe.properties");
                verify(environment, "probe.placeholder");
                verify(environment, "probe.yaml");
                verify(environment, "probe.tree");
                verify(environment, "probe.extra");
                environment.refresh();
                verify(environment, "probe.properties");
                verify(environment, "probe.yaml");
                verify(environment, "probe.tree");
            }
        }

        private static void verify(Environment environment, String key) {
            if (!"loaded".equals(environment.getRequiredProperty(key, String.class))) {
                throw new AssertionError("Failed to resolve " + key);
            }
        }
    }
}
